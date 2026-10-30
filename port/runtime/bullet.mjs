/** Loader for the locally built wasm32 Bullet module; never simulates a missing library. */
const EXPORTED = ['version', 'initLogging', 'loggingInitialised', 'worldInitialised', 'createWorld',
  'bodyCount', 'addBox', 'addSphere', 'stepSimulation', 'getTransform', 'removeAllBodies', 'disposeWorld',
  // Round 52: the game's Bullet.defineVehicleScript(name, float[]) JNI entry point. Our module
  // registers the parameter block for real; nothing here simulates a physics vehicle.
  'defineVehicleScript', 'vehicleScriptCount', 'vehicleScriptUniqueCount',
  'vehicleScriptOverwriteCount', 'vehicleScriptParamCount', 'vehicleScriptParam'];

async function loadManifest(locateFile) {
  const response = await fetch(locateFile('manifest.json'));
  if (!response.ok) throw new Error('pzbullet manifest unavailable (HTTP ' + response.status + ')');
  return response.json();
}

export async function bulletBridge({ locateFile = name => '/vendor/pzbullet/' + name } = {}) {
  const manifest = await loadManifest(locateFile);
  // The glue is a real ES module (Emscripten EXPORT_ES6), so both Chrome and Node import it.
  const module = await import(locateFile('pzbullet.js'));
  const instance = await module.default({ locateFile });
  // cwrap converts a char* return into a JS string; no manual UTF8ToString.
  const call = (name, signature) => {
    if (!instance.cwrap) throw new Error('pzbullet cwrap is unavailable');
    const function_ = instance.cwrap(name, signature[0], signature[1]);
    if (!function_) throw new Error('pzbullet export ' + name + ' is missing from the module');
    return function_;
  };
  const transformPointer = call('pzbullet_get_transform', ['number', ['number']]);
  const readVector = pointer => pointer ? Array.from(new Float64Array(instance.HEAPU8.buffer, pointer, 3)) : null;
  // The float block must reach C++ as floats. Emscripten's "array" marshaller writes single
  // bytes through HEAP8, which is the wrong element width, so the block goes through the module's
  // own scratch area: pzbullet_vehicle_scratch returns a pointer into module-owned memory and the
  // bridge fills it with a Float32Array view over the module heap (rebuilt per call because
  // ALLOW_MEMORY_GROWTH detaches older views). The module copies the values before returning.
  const scratchPointer = call('pzbullet_vehicle_scratch', ['number', ['number']]);
  const defineVehicleScriptRaw = call('pzbullet_define_vehicle_script', ['number', ['string', 'number', 'number']]);
  const defineVehicleScript = (name, params) => {
    // The module is the single validator (it rejects a null/empty name, a null block and a bad
    // count by storing nothing and returning 0). Passing null through cwrap yields a null char*,
    // and count 0 asks for no scratch, so invalid arguments reach C++ honestly instead of being
    // judged here.
    if (params !== null && params !== undefined && !(params instanceof Float32Array))
      throw new TypeError('defineVehicleScript parameters must be a Float32Array');
    const count = params ? params.length : 0;
    if (count > 0) {
      const pointer = scratchPointer(count);
      if (!pointer) throw new Error(`pzbullet_vehicle_scratch refused ${count} floats`);
      new Float32Array(instance.HEAPU8.buffer, pointer, count).set(params);
      return defineVehicleScriptRaw(name, pointer, count);
    }
    return defineVehicleScriptRaw(name, 0, 0);
  };
  const bridge = {
    manifest,
    exports: EXPORTED,
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
    vehicleScriptCount: call('pzbullet_vehicle_script_count', ['number', []]),
    vehicleScriptUniqueCount: call('pzbullet_vehicle_script_unique_count', ['number', []]),
    vehicleScriptOverwriteCount: call('pzbullet_vehicle_script_overwrite_count', ['number', []]),
    vehicleScriptParamCount: call('pzbullet_vehicle_script_param_count', ['number', ['number']]),
    vehicleScriptParam: call('pzbullet_vehicle_script_param', ['number', ['number', 'number']]),
    defineVehicleScript,
    // The module owns the scratch buffer; the caller copies the three doubles.
    getTransform(index) { return readVector(transformPointer(index)); },
  };
  // Library names this module honestly satisfies; loadLibrary only succeeds for them.
  bridge.loadedFor = Array.isArray(manifest.libraries) ? manifest.libraries.slice() : [];
  // A module without the declared exports must never look like a loaded library.
  for (const name of EXPORTED) {
    if (typeof bridge[name] !== 'function') throw new Error('pzbullet bridge export ' + name + ' is unavailable');
  }
  if (!bridge.version().startsWith('Bullet ')) throw new Error('pzbullet version string is not the compiled library identity');
  return bridge;
}

