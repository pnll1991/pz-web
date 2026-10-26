import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';

test('compiled real main is instantiated and its actual stop is recorded', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  const consoleMessages=[],pageErrors=[],wasm=[],failedResponses=[];
  page.on('console',message=>consoleMessages.push({type:message.type(),text:message.text()}));
  page.on('pageerror',error=>pageErrors.push(error.message));
  page.on('request',request=>{if(request.url().endsWith('.wasm'))wasm.push(new URL(request.url()).pathname);});
  page.on('response',response=>{if(response.status()>=400)failedResponses.push({path:new URL(response.url()).pathname,status:response.status()});});
  try {
    const response=await page.request.get('/game/build-state.json');expect(response.ok()).toBe(true);
    const build=await response.json();expect(build.success).toBe(true);expect(build.reflection).toBe('lua');expect(build.wasm_bytes).toBeGreaterThan(0);
    await page.goto('/game.html',{waitUntil:'domcontentloaded'});
    await expect.poll(()=>page.evaluate(()=>globalThis.pzGame?.phase),{timeout:60_000}).toMatch(/^(failed|main-returned|loader-failed)$/);
    const state=await page.evaluate(()=>globalThis.pzGame);
    await testInfo.attach('actual-main-state',{body:JSON.stringify({build,state,wasm},null,2),contentType:'application/json'});
    expect(wasm).toContain('/game/boot.wasm');expect(state.menuRendered).toBe(false);
    expect(state).toEqual({phase:'failed',error:'java.lang.LinkageError: Unknown platform: TeaVM',menuRendered:false});
    const offsets=consoleMessages.flatMap(message=>[...message.text.matchAll(/Throwable\.java:(\d+)/g)].map(match=>match[1]));
    // Resolve names/body boundaries only; never disassemble game code.
    const symbols=execFileSync('python3',['scripts/wasm_stack.py','port/target/web/game/boot.wasm',...offsets],{encoding:'utf8'});
    await testInfo.attach('actual-main-symbols',{body:symbols,contentType:'text/plain'});
    for(const name of ['zombie.gameStates.MainScreenState::main','org.lwjglx.opengl.Display::init','org.lwjglx.LWJGLUtil::<clinit>'])expect(symbols).toContain(name);
    expect(pageErrors).toEqual([]);
  } finally {
    await page.screenshot({path:'test-results/game-main-task4.png',fullPage:true});
    await testInfo.attach('actual-main-console',{body:JSON.stringify({consoleMessages,pageErrors,failedResponses},null,2),contentType:'application/json'});
  }
});
