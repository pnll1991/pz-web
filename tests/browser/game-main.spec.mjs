import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';

test('compiled real main is instantiated and its actual stop is recorded', async ({ page }, testInfo) => {
  test.setTimeout(330_000);
  const consoleMessages=[],pageErrors=[],wasm=[],failedResponses=[];
  let build,state,symbols;
  page.on('console',message=>consoleMessages.push({type:message.type(),text:message.text()}));
  page.on('pageerror',error=>pageErrors.push(error.message));
  page.on('request',request=>{if(request.url().endsWith('.wasm'))wasm.push(new URL(request.url()).pathname);});
  page.on('response',response=>{if(response.status()>=400)failedResponses.push({path:new URL(response.url()).pathname,status:response.status()});});
  try {
    const response=await page.request.get('/game/build-state.json');expect(response.ok()).toBe(true);
    build=await response.json();expect(build.success).toBe(true);expect(build.reflection).toBe('lua');expect(build.wasm_bytes).toBeGreaterThan(0);
    await page.goto('/game.html',{waitUntil:'domcontentloaded'});
    // Round 29: the RangeError is gone (Fiber.push no longer grows through the async
    // Arrays.copyOf(T[],int)), so the boot now reaches GameWindow.mainThreadInit() and the main
    // fiber parks there. It never reports a final phase, so the poll waits for the trace to stop
    // growing and then asserts the actual park point.
    await expect.poll(()=>page.evaluate(()=>(globalThis.pzGameTrace||[]).length),{timeout:190_000}).toBeGreaterThan(100);
    const traceLength=await page.evaluate(()=>(globalThis.pzGameTrace||[]).length);
    state=await page.evaluate(()=>globalThis.pzGame);
    await testInfo.attach('actual-main-state',{body:JSON.stringify({build,state,wasm},null,2),contentType:'application/json'});
    expect(wasm).toContain('/game/boot.wasm');expect(state.menuRendered).toBe(false);
    const trace=[state.trace || '',...consoleMessages.map(message=>message.text)].join('\n');
    const offsets=[...new Set([...trace.matchAll(/Throwable\.java:(\d+)/g)].map(match=>match[1]))];
    // Resolve names/body boundaries only; never disassemble game code.
    symbols=execFileSync('python3',['scripts/wasm_stack.py','port/target/web/game/boot.wasm',...offsets],{encoding:'utf8'});
    await testInfo.attach('actual-main-symbols',{body:symbols,contentType:'text/plain'});
    // Actual stop of round 29 (verified with a non-deduplicated boundary trace): the render thread
    // runs its first renderStep, GameWindow.mainThreadStart() calls mainThreadInit(), and
    // DebugOptions.init()/initMessaging() parks the main fiber after its last
    // ZomboidFileSystem#getMessagingDirSub() call. The fiber never reports a final phase and the
    // trace stops growing, so the honest assertion here is that phase is still 'starting' with no
    // page errors. The menu is not rendered.
    expect(state).toMatchObject({phase:'starting',error:'',menuRendered:false});
    expect(traceLength).toBeGreaterThan(100);
    // Round 29 close: the deduplicated list never rotates, so it is the reliable source for the
    // lifecycle boundaries; the ring is only evidence of the last events before the park.
    const boundary=await page.evaluate(()=>pzGameProgress.map(event=>event.label));
    expect(boundary).toContain('zombie.GameWindow#mainThreadInit()V:entered');
    expect(boundary).not.toContain('zombie.GameWindow#mainThreadInit()V:returned');
    expect(boundary).toContain('zombie.debug.DebugOptions#initMessaging()V:entered');
    expect(boundary).toContain('zombie.core.opengl.RenderThread#renderStep()Z:returned');
    // Save the raw park tail for the next round without disassembling anything.
    const ring=await page.evaluate(()=>(globalThis.pzGameTrace||[]).map(event=>event.label));
    await testInfo.attach('park-ring-tail',{body:ring.slice(-60).join('\n'),contentType:'text/plain'});
    const bullet = await page.evaluate(async()=>{const b=pzRuntime.bullet;return {loaded:!!b,loadedFor:b?.loadedFor,version:b?.version(),logging:b?.loggingInitialised(),world:b?.createWorld(0,-9.82,0),count:b?.bodyCount(),manifest:b?.manifest?.bullet?.version,wasmSha:b?.manifest?.module?.wasm_sha256};});
    expect(bullet.loaded).toBe(true); expect(bullet.loadedFor).toContain('PZBullet64'); expect(bullet.manifest).toBe('2.83');
    expect(bullet.version).toMatch(/^Bullet 283 \(upstream bullet3-2\.83, wasm32 port build\)$/);
    expect(bullet.world).toBe(1); expect(bullet.count).toBe(0);
    expect(bullet.wasmSha).toBe(JSON.parse(readFileSync('port/target/web/vendor/pzbullet/manifest.json','utf8')).module.wasm_sha256);
    // Frames of this round's logged exception, resolved from the offsets its trace reports:
    // oshi.SystemInfoFFM.createHardware throws "Unsupported platform: UNSUPPORTED" from
    // MainScreenState.printSpecs, which the game catches and logs (it is not the blocker).
    for(const name of ['zombie.gameStates.MainScreenState::main','zombie.gameStates.MainScreenState::printSpecs',
        'oshi.SystemInfoFFM::createHardware','java.lang.UnsupportedOperationException::<init>_0',
        'port.GameBoot::launch','org.teavm.runtime.Fiber::startMain'])
        expect(symbols).toContain(name);
    const progress=await page.evaluate(()=>pzGameProgress.map(event=>event.label));
    expect(progress).toContain('org.lwjglx.opengl.Display#init()V:returned');
    expect(progress).toContain('zombie.core.physics.Bullet#init()V:entered');
    expect(progress).toContain('zombie.core.physics.Bullet#init()V:returned');
    expect(progress).not.toContain('zombie.core.physics.Bullet#initPZBullet()V:returned');
    expect(progress).not.toContain('zombie.Lua.LuaManager#init()V:returned');
    const windowSystem=await page.evaluate(()=>pzRuntime.window.events);
    const types=windowSystem.map(event=>event.type);
    // Window lifecycle actually reached this round (verified against window.mjs events).
    expect(types).toContain('initialized');
    expect(types).toContain('created');
    expect(types.filter(type=>type==='created').length).toBe(1);
    // Display$Callbacks.initCallbacks installs 10 kinds plus the error callback installed earlier.
    // Later the joystick callback (kind 11) is registered and zombie.core.Clipboard re-installs the
    // error callback with null twice, so only the minimum count is asserted here.
    expect(types.filter(type=>type==='callback_registered').length).toBeGreaterThanOrEqual(11);
    const created=windowSystem.find(event=>event.type==='created');
    expect(created).toMatchObject({backend:'browser',title:'Project Zomboid'});
    // The STB pixel bridge really decoded media/ui/zomboidIcon32.png and uploaded RGBA8 pixels.
    const icon=windowSystem.find(event=>event.type==='icon_set');
    expect(icon).toMatchObject({backend:'browser',width:32,height:32,bytes:4096});
    expect(await page.evaluate(()=>pzRuntime.window.lastIcon)).toMatchObject({width:32,height:32,bytes:4096});
    expect(await page.evaluate(()=>document.querySelector('link[rel="icon"]')?.href?.startsWith('data:image/png') ?? false)).toBe(true);
    // Display.showWindow and glfwMakeContextCurrent run before the GL capability stop.
    expect(types).toContain('shown');
    expect(types).toContain('context_current');
    // Display.create() really returns now (round 25 GL capability bridge), and Controllers.create
    // registers the joystick callback as kind 11 over the Gamepad API.
    expect(progress).toContain('org.lwjglx.opengl.Display#create()V:returned');
    const registrations = windowSystem.filter(event=>event.type==='callback_registered');
    expect(registrations.some(event=>event.kind===11)).toBe(true);
    expect(types).toContain('attribute_ignored');
    expect(trace).not.toContain('failed to read image');
    // The only exception the game logs this round is the oshi hardware probe; MainScreenState.main
    // itself never returns, so there is no "Exception thrown during MainScreenState.main" yet.
    expect(trace).toContain('Unsupported platform: UNSUPPORTED');
    expect(trace).toContain('Unable to determine available GPU memory');
    expect(pageErrors).toEqual([]);
  } finally {
    const screenshot='test-results/game-main-round29.png';
    await page.screenshot({path:screenshot,fullPage:true});
    const windowSystem=await page.evaluate(()=>globalThis.pzRuntime?.window?.events || []);
    const progress=await page.evaluate(()=>globalThis.pzGameProgress || []);
    const ring=await page.evaluate(()=>globalThis.pzGameTrace || []);
    // Call counters that reveal which bridge is looping when the wasm overflows the JS stack.
    const bridge=await page.evaluate(()=>({
      glCalls:globalThis.pzRuntime?.gl?.calls || null,
      glCallTotal:Object.values(globalThis.pzRuntime?.gl?.calls || {}).reduce((a,b)=>a+b,0),
      dispatched:globalThis.pzRuntime?.window?.dispatchedEvents ?? null,
      swapCount:globalThis.pzRuntime?.window?.swapCount ?? null,
      inputMode:globalThis.pzRuntime?.window?.inputMode ?? null,
      pointerLock:globalThis.pzRuntime?.window?.pointerLock ?? null,
      pointerLockError:globalThis.pzRuntime?.window?.pointerLockError ?? null,
    }));
    mkdirSync('port/target/reports/game-main-lua',{recursive:true});
    writeFileSync('port/target/reports/game-main-lua/chrome-startup.json',JSON.stringify({
      observedAt:new Date().toISOString(),build,state,wasm,windowSystem,progress,bridge,
      parkRing:ring,
      console:{consoleMessages,pageErrors,failedResponses},symbols,screenshot,
      screenshot_visually_checked:false
    },null,2)+'\n');
    await testInfo.attach('actual-main-console',{body:JSON.stringify({consoleMessages,pageErrors,failedResponses},null,2),contentType:'application/json'});
  }
});
