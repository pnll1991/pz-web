import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';

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
    expect(state).toMatchObject({phase:'failed',error:'java.lang.UnsatisfiedLinkError: System.loadLibrary: browser implementation is missing for PZBullet64',menuRendered:false});
    for(const name of ['zombie.gameStates.MainScreenState::main','zombie.core.physics.Bullet::init','zombie.core.physics.Bullet::loadLibrary'])expect(symbols).toContain(name);
    const progress=await page.evaluate(()=>pzGameProgress.map(event=>event.label));
    expect(progress).toContain('org.lwjglx.opengl.Display#init()V:returned');
    expect(progress).toContain('zombie.core.physics.Bullet#init()V:entered');
    expect(progress).not.toContain('zombie.core.physics.Bullet#init()V:returned');
    expect(progress).not.toContain('zombie.Lua.LuaManager#init()V:returned');
    const windowSystem=await page.evaluate(()=>pzRuntime.window.events);
    expect(windowSystem).toHaveLength(1);
    expect(windowSystem[0]).toMatchObject({type:'initialized',backend:'browser',refreshRate:0});
    expect(pageErrors).toEqual([]);
  } finally {
    const screenshot='test-results/game-main-round5.png';
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
