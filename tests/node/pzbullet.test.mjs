/** Real wasm32 Bullet module behavior test; the module is the locally compiled one. */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const vendor = path.resolve('port/target/web/vendor/pzbullet');

/**
 * The real runtime bridge (port/runtime/bullet.mjs) is imported through Node's file loader, so
 * only its manifest fetch has to be answered from disk. Response/Request exist in Node 24.
 */
async function runtimeBridge() {
  const {bulletBridge} = await import(pathToFileURL(path.resolve('port/runtime/bullet.mjs')).href);
  const locateFile = name => pathToFileURL(path.join(vendor, name)).href;
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url) => new Response(await readFile(fileURLToPath(url)), {headers:{'Content-Type':'application/json'}});
  try { return await bulletBridge({ locateFile }); } finally { globalThis.fetch = realFetch; }
}

async function loadBridge() {
  const locateFile = name => pathToFileURL(path.join(vendor, name)).href;
  const manifest = JSON.parse(await readFile(path.join(vendor, 'manifest.json'), 'utf8'));
  const module = await import(pathToFileURL(path.join(vendor, 'pzbullet.js')).href);
  const instance = await module.default({ locateFile });
  const call = (name, signature, returns) => {
    const function_ = instance.cwrap(name, signature[0], signature[1]);
    assert.ok(function_, 'export ' + name + ' must exist in the compiled module');
    return function_;
  };
  const transformPointer = call('pzbullet_get_transform', ['number', ['number']]);
  const readVector = pointer => pointer ? Array.from(new Float64Array(instance.HEAPU8.buffer, pointer, 3)) : null;
  return { manifest, instance, bridge: {
    version: call('pzbullet_version', ['string', []]),
    initLogging: call('pzbullet_init_logging', ['void', []]),
    loggingInitialised: call('pzbullet_logging_initialised', ['number', []]),
    loggingBufferBytes: call('pzbullet_logging_buffer_bytes', ['number', []]),
    worldInitialised: call('pzbullet_world_initialised', ['number', []]),
    createWorld: call('pzbullet_create_world', ['number', ['number', 'number', 'number']]),
    bodyCount: call('pzbullet_body_count', ['number', []]),
    addBox: call('pzbullet_add_box', ['number', ['number', 'number', 'number', 'number', 'number', 'number', 'number', 'number', 'number', 'number', 'number']]),
    addSphere: call('pzbullet_add_sphere', ['number', ['number', 'number', 'number', 'number', 'number']]),
    stepSimulation: call('pzbullet_step_simulation', ['number', ['number', 'number', 'number']]),
    removeAllBodies: call('pzbullet_remove_all_bodies', ['void', []]),
    disposeWorld: call('pzbullet_dispose_world', ['void', []]),
    getTransform: index => readVector(transformPointer(index)),
  } };
}

test('compiled Bullet module reports its real version from the pinned source macro', async () => {
  const { manifest, bridge } = await loadBridge();
  assert.equal(manifest.bullet.version, '2.83');
  assert.equal(manifest.bullet.bt_bullet_version_macro, 283);
  const version = bridge.version();
  assert.match(version, /^Bullet 283 \(upstream bullet3-2\.83, wasm32 port build\)$/, 'version string is derived from BT_BULLET_VERSION');
  assert.equal(bridge.loggingInitialised(), 0, 'logging starts uninitialized');
  assert.equal(bridge.loggingBufferBytes(), 0, 'no logging buffer is claimed before real allocation');
  assert.equal(bridge.worldInitialised(), 0, 'no world is claimed before createWorld');
  bridge.initLogging();
  assert.equal(bridge.loggingInitialised(), 1);
  assert.equal(bridge.loggingBufferBytes(), 4096, 'init logging really allocates its own buffer');
  bridge.initLogging();
  assert.equal(bridge.loggingInitialised(), 1, 'a repeated init stays ready without a second buffer');
  bridge.disposeWorld();
});

test('a dynamic box falls under gravity through real Bullet simulation steps', async () => {
  const { bridge } = await loadBridge();
  assert.equal(bridge.createWorld(0, -9.82, 0), 1);
  assert.equal(bridge.worldInitialised(), 1, 'an existing world is reported as initialised');
  assert.equal(bridge.createWorld(0, 0, 0), 0, 'a second world must be refused');
  const ground = bridge.addBox(50, 1, 50, 0, 0, 0, 0, 0, 0, 0, 1);
  const box = bridge.addBox(0.5, 0.5, 0.5, 80, 0, 12, 0, 0, 0, 0, 1);
  const sphere = bridge.addSphere(0.4, 10, 3, 9, 0);
  assert.equal(ground, 0); assert.equal(box, 1); assert.equal(sphere, 2);
  assert.equal(bridge.bodyCount(), 3);
  const start = bridge.getTransform(box);
  assert.deepEqual(start, [0, 12, 0]);
  let totalSteps = 0;
  for (let tick = 0; tick < 60; tick++) totalSteps += bridge.stepSimulation(1 / 30, 10, 1 / 60);
  assert.ok(totalSteps > 0, 'stepSimulation must report executed substeps');
  const fallen = bridge.getTransform(box);
  assert.ok(fallen[1] < start[1] - 5, 'the box must have fallen well below its start: ' + fallen[1]);
  assert.ok(fallen[1] > 1 + 0.5 - 0.2, 'the box rests on the ground, not through it: ' + fallen[1]);
  assert.deepEqual(bridge.getTransform(99), null, 'unknown bodies report null');
  bridge.removeAllBodies();
  assert.equal(bridge.bodyCount(), 0);
  bridge.disposeWorld();
  assert.equal(bridge.bodyCount(), -1, 'after disposal there is no world');
  assert.equal(bridge.worldInitialised(), 0, 'disposal really removes the world');
  assert.equal(bridge.stepSimulation(1 / 30, 1, 1 / 60), -1);
});

test('defineVehicleScript registers the game parameter block in the compiled module', async () => {
  const bridge = await runtimeBridge();
  assert.ok(bridge.exports.includes('defineVehicleScript'), 'the bridge must declare the entry point');
  assert.equal(bridge.vehicleScriptCount(), 0, 'a fresh module has no registered scripts');
  assert.equal(bridge.vehicleScriptUniqueCount(), 0);
  assert.equal(bridge.vehicleScriptOverwriteCount(), 0);

  // VehicleScript.toBullet fills a float[200]: 11 scalars, 5 floats per wheel, then shapes.
  const params = new Float32Array(200);
  const block = [1.0, 1500, 0.98, 55, 0.3, 4.0, 36, 20, 1.5, 5.0, 4];
  block.forEach((value, i) => { params[i] = value; });
  for (let wheel = 0; wheel < 4; wheel++)
    [wheel < 2 ? 1 : 0, 1.2 + wheel, -0.4, 0.8 - wheel, 0.35].forEach((value, i) => { params[11 + wheel * 5 + i] = value; });
  params[31] = 2;                      // numShapes
  params[32] = 1;                      // chassis shape
  params[33] = 0.1; params[34] = 0.2; params[35] = 0.3;
  params[36] = 1.0; params[37] = 0.7; params[38] = 1.9;

  assert.equal(bridge.defineVehicleScript('vehicle.car.gasguzler', params), 1, 'first registration is new');
  assert.equal(bridge.vehicleScriptCount(), 1);
  assert.equal(bridge.vehicleScriptUniqueCount(), 1);
  assert.equal(bridge.vehicleScriptParamCount(0), 200, 'the whole block is stored, not a truncated copy');
  for (const index of [0, 1, 8, 10, 11, 15, 25, 31, 38])
    assert.equal(bridge.vehicleScriptParam(0, index), params[index], `param ${index} must survive the float copy`);
  assert.equal(bridge.vehicleScriptParam(0, 200), 0, 'an out-of-range index is reported as 0');
  assert.equal(bridge.vehicleScriptParamCount(1), -1, 'an unknown slot is reported as -1');

  // The game loads the same script again (reload/new scene): the name must not duplicate.
  assert.equal(bridge.defineVehicleScript('vehicle.car.gasguzler', params), 2, 'a redefinition is reported as an overwrite');
  assert.equal(bridge.vehicleScriptCount(), 1, 'a redefinition must not grow the registry');
  assert.equal(bridge.vehicleScriptOverwriteCount(), 1);

  assert.equal(bridge.defineVehicleScript('vehicle.truck.flatbed', params.subarray(0, 40)), 1);
  assert.equal(bridge.vehicleScriptCount(), 2);
  assert.equal(bridge.vehicleScriptUniqueCount(), 2);
  assert.equal(bridge.vehicleScriptParamCount(1), 40);

  // Rejected arguments store nothing (no simulated success): the compiled module decides.
  assert.equal(bridge.defineVehicleScript('', params), 0, 'an empty name is rejected');
  assert.equal(bridge.defineVehicleScript(null, params), 0, 'a missing name is rejected');
  assert.equal(bridge.defineVehicleScript('x', new Float32Array(0)), 0, 'an empty block is rejected');
  assert.equal(bridge.defineVehicleScript(null, null), 0, 'a missing block is rejected');
  assert.equal(bridge.vehicleScriptCount(), 2, 'rejected calls must not add entries');
  assert.equal(bridge.vehicleScriptOverwriteCount(), 1, 'rejected calls must not count as overwrites');
  assert.throws(() => bridge.defineVehicleScript('x', [1, 2, 3]), TypeError,
                'a plain JS array would be marshalled as bytes, so the bridge refuses it');
});
