import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';

test('compiled real main is instantiated and its actual stop is recorded', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
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
    await expect.poll(()=>page.evaluate(()=>globalThis.pzGame?.phase),{timeout:60_000}).toMatch(/^(failed|main-returned|loader-failed)$/);
    state=await page.evaluate(()=>globalThis.pzGame);
    await testInfo.attach('actual-main-state',{body:JSON.stringify({build,state,wasm},null,2),contentType:'application/json'});
    expect(wasm).toContain('/game/boot.wasm');expect(state.menuRendered).toBe(false);
    const trace=[state.trace || '',...consoleMessages.map(message=>message.text)].join('\n');
    const offsets=[...new Set([...trace.matchAll(/Throwable\.java:(\d+)/g)].map(match=>match[1]))];
    // Resolve names/body boundaries only; never disassemble game code.
    symbols=execFileSync('python3',['scripts/wasm_stack.py','port/target/web/game/boot.wasm',...offsets],{encoding:'utf8'});
    await testInfo.attach('actual-main-symbols',{body:symbols,contentType:'text/plain'});
    // Actual stop of this round: the window lifecycle runs on the canvas (create window,
    // register callbacks, show window, make context current) and the main returns because
    // the WebGL2 GL capability bridge is not implemented yet; the menu is still not rendered.
    expect(state).toMatchObject({phase:'main-returned',error:'',menuRendered:false});
    const bullet = await page.evaluate(async()=>{const b=pzRuntime.bullet;return {loaded:!!b,loadedFor:b?.loadedFor,version:b?.version(),logging:b?.loggingInitialised(),world:b?.createWorld(0,-9.82,0),count:b?.bodyCount(),manifest:b?.manifest?.bullet?.version,wasmSha:b?.manifest?.module?.wasm_sha256};});
    expect(bullet.loaded).toBe(true); expect(bullet.loadedFor).toContain('PZBullet64'); expect(bullet.manifest).toBe('2.83');
    expect(bullet.version).toMatch(/^Bullet 283 \(upstream bullet3-2\.83, wasm32 port build\)$/);
    expect(bullet.world).toBe(1); expect(bullet.count).toBe(0);
    expect(bullet.wasmSha).toBe(JSON.parse(readFileSync('port/target/web/vendor/pzbullet/manifest.json','utf8')).module.wasm_sha256);
    for(const name of ['zombie.gameStates.MainScreenState::main','zombie.core.physics.Bullet::init','zombie.core.physics.Bullet::initPZBullet'])expect(symbols).toContain(name);
    const progress=await page.evaluate(()=>pzGameProgress.map(event=>event.label));
    expect(progress).toContain('org.lwjglx.opengl.Display#init()V:returned');
    expect(progress).toContain('zombie.core.physics.Bullet#init()V:entered');
    expect(progress).toContain('zombie.core.physics.Bullet#init()V:returned');
    expect(progress).not.toContain('zombie.core.physics.Bullet#initPZBullet()V:returned');
    expect(progress).not.toContain('zombie.Lua.LuaManager#init()V:returned');
    const windowSystem=await page.evaluate(()=>pzRuntime.window.events);
    const types=windowSystem.map(event=>event.type);
    // Window lifecycle actually reached this round (verified against window.mjs events).
    expect(types).toContain('created');
    const created=windowSystem.find(event=>event.type==='created');
    expect(created).toMatchObject({handle:1});
    expect(types.filter(type=>type==='callback_registered').length).toBeGreaterThanOrEqual(10);
    expect(types).toContain('shown');
    expect(types).toContain('context_current');
    // The honest stop: WebGL2 capability bridge is not implemented, so GL.createCapabilities
    // raises with the exact operation name instead of pretending a GL context exists.
    expect(trace).toContain('Browser WebGL2 GL capability bridge is not implemented: org.lwjgl.opengl.GL#createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;');
    expect(trace).toContain('Exception thrown during MainScreenState.main');
    expect(pageErrors).toEqual([]);
  } finally {
    const screenshot='test-results/game-main-round19.png';
    await page.screenshot({path:screenshot,fullPage:true});
    const windowSystem=await page.evaluate(()=>globalThis.pzRuntime?.window?.events || []);
    const progress=await page.evaluate(()=>globalThis.pzGameProgress || []);
    mkdirSync('port/target/reports/game-main-lua',{recursive:true});
    writeFileSync('port/target/reports/game-main-lua/chrome-startup.json',JSON.stringify({
      observedAt:new Date().toISOString(),build,state,wasm,windowSystem,progress,
      console:{consoleMessages,pageErrors,failedResponses},symbols,screenshot,
      screenshot_visually_checked:false
    },null,2)+'\n');
    await testInfo.attach('actual-main-console',{body:JSON.stringify({consoleMessages,pageErrors,failedResponses},null,2),contentType:'application/json'});
  }
});
