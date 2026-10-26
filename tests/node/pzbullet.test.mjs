/** Real wasm32 Bullet module behavior test; the module is the locally compiled one. */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const vendor = path.resolve('port/target/web/vendor/pzbullet');

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
