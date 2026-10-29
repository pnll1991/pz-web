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
    const canvasWidth=pzRuntime.input.canvas.width;
    const canvasHeight=pzRuntime.input.canvas.height;
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
    const metrics = {monitorX:bridge.monitorX(),monitorY:bridge.monitorY(),
      workareaWidth:bridge.monitorWorkareaWidth(),workareaHeight:bridge.monitorWorkareaHeight(),
      bufferWidth:bridge.bufferWidth(),bufferHeight:bridge.bufferHeight()};
    let uninit;
    try { new BrowserWindow(runtime.input.canvas,runtime.gl,runtime.input).monitorX(); }
    catch (error) { uninit = error.message; }
    return {before,first,second,expected:`${screen.width}:${screen.height}:${screen.colorDepth}`,
      metrics,uninit,
      canvasWidth,canvasHeight,screen:{width:screen.width,height:screen.height,depth:screen.colorDepth},
      events:bridge.events,unavailable,layoutLabel,nonPrintable,invalid};
  });
  expect(result.before).toBe(false);
  expect(result.first).toContain(result.expected);expect(result.second).toContain(result.first);
  // The page's single display: own handle (never the window handle 1), origin (0,0),
  // work area = the whole screen, and the mode = the canvas drawing buffer.
  const [display,monitor,mode,invalidMonitor] = result.first.split('|');
  expect(display).toBe(result.expected);
  const [handle,workarea] = monitor.split(':');
  expect(Number(handle)).toBe(2);
  expect(Number(handle)).not.toBe(1);
  expect(workarea).toBe(`0,0,${result.screen.width},${result.screen.height}`);
  expect(result.metrics).toEqual({monitorX:0,monitorY:0,workareaWidth:result.screen.width,
    workareaHeight:result.screen.height,bufferWidth:result.canvasWidth,bufferHeight:result.canvasHeight});
  expect(result.uninit).toContain('is not initialized');
  const bits = result.screen.depth >= 3 ? Math.trunc(result.screen.depth / 3) * 3 : 0;
  expect(mode).toBe(`${result.canvasWidth}x${result.canvasHeight}:${bits}:0`);
  expect(invalidMonitor).toContain('Invalid browser monitor handle');
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

test('GLFW context attach and detach are reported honestly to the game', async ({ page }) => {
  await page.goto('/');await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const result=await page.evaluate(async()=>{
    const {BrowserWindow}=await import('/runtime/window.mjs');
    const runtime=pzRuntime;
    const bridge=new BrowserWindow(runtime.input.canvas,runtime.gl,runtime.input);
    bridge.init();bridge.create(640,480,0,'prueba');
    const before=bridge.getCurrentContext();
    bridge.makeContextCurrent(1);
    const current=bridge.getCurrentContext();
    const detached=bridge.detachContext();
    const after=bridge.getCurrentContext();
    // Handle 0 is mapped to detachContext() by port.shim.BrowserWindow, so the JS backend must
    // still refuse it as a window handle.
    let invalid;
    try { bridge.makeContextCurrent(0); } catch(error) { invalid=error.message; }
    return {before,current,detached,after,invalid,
      contextEvents:bridge.events.filter(e=>String(e.type).startsWith('context')).map(e=>e.type)};
  });
  expect(result.before).toBe(0);
  expect(result.current).toBe(1);
  expect(result.detached).toBe(0);
  expect(result.after).toBe(0);
  expect(result.invalid).toContain('Invalid browser window handle: 0');
  expect(result.contextEvents).toEqual(['context_current','context_detached']);
});
