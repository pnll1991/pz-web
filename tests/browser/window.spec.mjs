import { test, expect } from '@playwright/test';

test('Java window initialization uses actual browser metrics and is idempotent', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const result=await page.evaluate(async()=>{
    const {BrowserWindow}=await import('/runtime/window.mjs');
    const runtime=pzRuntime;
    const bridge=runtime.window=new BrowserWindow(runtime.input.canvas,runtime.gl,runtime.input);
    await bridge.prepare();
    const before=bridge.initialized;
    const first=pzLab.teavm.exports.windowSystemProbe();
    const second=pzLab.teavm.exports.windowSystemProbe();
    // Missing layout information must stay unavailable, never invent US labels.
    bridge.layout=null;
    const unavailable=bridge.keyName(65,65);
    bridge.layout=new Map([['KeyA','q'],['Enter','\r']]);
    const layoutLabel=bridge.keyName(-1,65);
    const nonPrintable=bridge.keyName(257,257);
    let invalid;
    try { bridge.key(1,65); } catch(error) { invalid=error.message; }
    return {before,first,second,expected:`${screen.width}:${screen.height}:${screen.colorDepth}`,
      events:bridge.events,unavailable,layoutLabel,nonPrintable,invalid};
  });
  expect(result.before).toBe(false);
  expect(result.first).toBe(result.expected);expect(result.second).toBe(result.first);
  expect(result.events).toHaveLength(1);
  expect(result.events[0]).toMatchObject({type:'initialized',backend:'browser',refreshRate:0});
  expect(result.events[0].renderer).toContain('WebGL 2.0');
  expect(result.unavailable).toBeNull();expect(result.layoutLabel).toBe('q');
  expect(result.nonPrintable).toBeNull();
  expect(result.invalid).toContain('Invalid browser window handle');
});

test('window initialization rejects detached canvases and lost graphics contexts', async ({ page }) => {
  await page.goto('/');await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const errors=await page.evaluate(async()=>{
    const {BrowserWindow}=await import('/runtime/window.mjs');
    const detached=document.createElement('canvas');
    const failures=[];
    for(const bridge of [new BrowserWindow(detached,pzRuntime.gl,{canvas:detached}),
      new BrowserWindow(pzRuntime.input.canvas,{...pzRuntime.gl,lost:true},pzRuntime.input)]) {
      try {bridge.init();failures.push('unexpected success');}catch(error){failures.push(error.message);}
      if(bridge.initialized)throw new Error('Failure marked initialized');
    }
    return failures;
  });
  expect(errors).toHaveLength(2);
  for(const error of errors)expect(error).toContain('requires a live canvas');
});
