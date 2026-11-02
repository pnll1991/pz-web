import { test, expect } from '@playwright/test';

test('substituted Java concurrency yields, preserves order and wakes conditions', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await page.evaluate(() => pzLab.teavm.exports.checkCompatibility());
  await expect.poll(() => page.evaluate(() => pzCompatibility.status)).toBe('passed');
  expect(await page.evaluate(() => pzCompatibility.detail)).toBe('queues,futures,executor,condition:OK;channels,force,printstream,fma,matcher,crc:OK');
  await page.reload(); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(() => [...pzLab.vfs.readFile('/Zomboid/channel-check.bin')])).toEqual([1,9]);
  await page.evaluate(() => { pzLab.vfs.flush = () => Promise.reject(new Error('fixture-durability-failure')); pzLab.teavm.exports.checkCompatibility(); });
  await expect.poll(() => page.evaluate(() => pzCompatibility.status)).toBe('failed');
  expect(await page.evaluate(() => pzCompatibility.detail)).toContain('fixture-durability-failure');
});

test('Java actually executes in Wasm GC and renders a WebGL2 shader', async ({ page }, testInfo) => {
  const errors = []; page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/lab.html');
  await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const result = await page.evaluate(()=>({boot:pzBoot,pixel:pzLab.pixel,isolated:crossOriginIsolated,
    actualWasm:pzLab.teavm.instance instanceof WebAssembly.Instance, metrics:pzLab.metrics()}));
  expect(result.actualWasm).toBe(true); expect(result.isolated).toBe(true);
  expect(result.boot.missingNative).toBe('Java_port_demo_NativeProbe_missing__I');
  expect(result.boot.bufferChecksum).toBe(170); expect(result.boot.gameInitialized).toBe(false);
  expect(result.boot.javaVfs).toBe(true);
  expect(result.pixel).toEqual([64,217,166,255]);
  await expect.poll(()=>page.evaluate(()=>pzLab.teavm.exports.frameCount())).toBeGreaterThan(3);
  expect(errors).toEqual([]);
  await page.screenshot({path:'test-results/platform.png',fullPage:true});
  await testInfo.attach('platform-metrics',{body:JSON.stringify(await page.evaluate(()=>pzLab.metrics()),null,2),contentType:'application/json'});
});

test('preloaded files read synchronously and /Zomboid persists after reload', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(()=>new TextDecoder().decode(pzLab.vfs.readFile('/media/platform-probe.txt')))).toContain('Recurso sintético');
  await page.locator('#note').fill('Partida de prueba — áéí 123');
  await page.getByRole('button',{name:'Guardar',exact:true}).click();
  await expect(page.locator('#saved')).toContainText('Guardado.');
  await page.reload(); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await expect(page.locator('#note')).toHaveValue('Partida de prueba — áéí 123');
  expect(await page.evaluate(()=>pzLab.vfs.exists('/Zomboid/platform-note.txt.tmp'))).toBe(false);
  await page.evaluate(async()=>{
    pzLab.vfs.writeFile('/Zomboid/unflushed',new Uint8Array([1]));
  });
  await page.reload(); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(()=>pzLab.vfs.exists('/Zomboid/unflushed'))).toBe(false);
  await expect(page.locator('#note')).toHaveValue('Partida de prueba — áéí 123');
});

test('input releases held keys on blur and loop can yield and pause', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await page.locator('#canvas').focus(); await page.keyboard.down('w');
  expect(await page.evaluate(()=>pzLab.input.keys.has(87))).toBe(true);
  await page.locator('#note').focus();
  expect(await page.evaluate(()=>pzLab.input.keys.size)).toBe(0);
  await page.keyboard.up('w'); await page.keyboard.insertText('ñ');
  expect(await page.evaluate(()=>pzLab.input.events.some(e=>e.type==='text' && e.text==='ñ'))).toBe(true);
  await page.getByRole('button',{name:'Pausar loop'}).click();
  const stopped = await page.evaluate(()=>pzLab.teavm.exports.frameCount());
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  expect(await page.evaluate(()=>pzLab.teavm.exports.frameCount())).toBe(stopped);
  await page.getByRole('button',{name:'Reanudar loop'}).click();
  await expect.poll(()=>page.evaluate(()=>pzLab.teavm.exports.frameCount())).toBeGreaterThan(stopped);
});

test('missing graphics and invalid shaders report failure', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(()=>{
    try { pzLab.gl.unsupported('Java_fixture_GL_glMissing'); } catch(e) { return e.message; }
  })).toContain('Java_fixture_GL_glMissing');
  expect(await page.evaluate(()=>{
    try { pzLab.gl.shader(pzLab.gl.context.FRAGMENT_SHADER,'invalid shader'); } catch(e) { return e.message; }
  })).toContain('Shader compilation failed');
  expect(await page.evaluate(()=>{
    pzLab.gl.context.enable(0); // Deliberately invalid enum in our local test context.
    return [pzLab.teavm.exports.glError(), pzLab.teavm.exports.glError()];
  })).toEqual([1280,0]);
});

test('translated desktop uniforms and shader output preserve their values', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const result = await page.evaluate(() => {
    const bridge = pzLab.gl, gl = bridge.context;
    const vertex = '#version 330 core\nconst vec2 p[3]=vec2[3](vec2(-1.,-1.),vec2(1.,-1.),vec2(0.,1.));void main(){gl_Position=vec4(p[gl_VertexID],0.,1.);}';
    const fragment = '#version 120\nuniform vec4 tint=vec4(.25,.5,.75,1.);void main(){gl_FragColor=tint;}';
    const linked = bridge.desktopProgram(vertex,fragment);
    const vao = gl.createVertexArray();
    try {
      gl.bindVertexArray(vao); gl.useProgram(linked.program); gl.drawArrays(gl.TRIANGLES,0,3);
      const pixel = new Uint8Array(4);
      gl.readPixels(gl.drawingBufferWidth>>1,gl.drawingBufferHeight>>1,1,1,gl.RGBA,gl.UNSIGNED_BYTE,pixel);
      return {pixel:[...pixel],uniform:[...gl.getUniform(linked.program,gl.getUniformLocation(linked.program,'tint'))],error:gl.getError()};
    } finally { gl.bindVertexArray(null); gl.useProgram(null); gl.deleteVertexArray(vao); gl.deleteProgram(linked.program); }
  });
  expect(result).toEqual({pixel:[64,128,191,255],uniform:[.25,.5,.75,1],error:0});
  await page.screenshot({path:'test-results/task3-shader-compatibility.png',fullPage:true});
});

test('generated resource packs load, verify hashes and reuse Cache Storage', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const loaded = await page.evaluate(async()=>{
    const report = await pzLab.preloadPacks(pzLab.vfs);
    return {report,text:new TextDecoder().decode(pzLab.vfs.readFile('/media/probe.txt'))};
  });
  expect(loaded.report.files).toBe(1); expect(loaded.text).toContain('Paquete sintético');
  let blobRequests = 0;
  await page.route('**/packs/*.bin',route=>{blobRequests++;return route.abort();});
  await page.reload(); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await page.evaluate(()=>pzLab.preloadPacks(pzLab.vfs));
  expect(blobRequests).toBe(0);
  const corrupted = await page.evaluate(async()=>{
    const cache = await caches.open('pz-web-packs-v1');
    const key = (await cache.keys())[0];
    await cache.put(key,new Response('corrupted'));
    try { await pzLab.preloadPacks(pzLab.vfs); } catch(error) { return error.message; }
  });
  expect(corrupted).toContain('Pack integrity mismatch');
});

test('local fixture mount is readable and its byte ranges match', async ({ request }) => {
  const response = await request.get('/media/probe.txt'); expect(response.status()).toBe(200);
  const data = await response.body();
  const partial = await request.get('/media/probe.txt',{headers:{Range:'bytes=0-6'}});
  expect(partial.status()).toBe(206); expect(await partial.body()).toEqual(data.subarray(0,7));
});

test('an aborted IndexedDB save retains the previous committed snapshot', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const aborted = await page.evaluate(async()=>{
    const vfs = pzLab.vfs;
    vfs.writeFile('/Zomboid/atomic.txt',new TextEncoder().encode('previous'));
    await vfs.flush();
    const transaction = vfs.database.transaction('files','readwrite');
    const aborted = new Promise(resolve=>{transaction.onabort=()=>resolve(true);});
    transaction.objectStore('files').put(new TextEncoder().encode('interrupted'),'/Zomboid/atomic.txt');
    transaction.abort();
    return aborted;
  });
  expect(aborted).toBe(true);
  await page.reload(); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(()=>new TextDecoder().decode(pzLab.vfs.readFile('/Zomboid/atomic.txt')))).toBe('previous');
});

test('java.io, RandomAccessFile and NIO share the persistent browser filesystem', async ({ page }) => {
  await page.goto('/lab.html'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(() => pzLab.teavm.exports.readJavaFile('/media/platform-probe.txt'))).toContain('sintético');
  expect(await page.evaluate(() => pzLab.teavm.exports.exerciseJavaFiles('/Zomboid/java-io/nested'))).toBe('java NIO: ñ😀');
  expect(await page.evaluate(() => {
    pzLab.vfs.mount('/media/binary-probe.bin', new Uint8Array([0, 128, 255]));
    return pzLab.teavm.exports.readUnsignedBytes('/media/binary-probe.bin');
  })).toBe(true);
  await page.evaluate(async () => {
    if (!pzLab.vfs.mkdir('/Zomboid/empty-dir')) throw new Error('mkdir');
    pzLab.vfs.setAttribute('/Zomboid/java-io/nested/nio.txt', 'modified', 123456000);
    await pzLab.vfs.flush();
  });
  await page.reload(); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(() => pzLab.teavm.exports.readJavaFile('/Zomboid/java-io/nested/nio.txt'))).toBe('java NIO: ñ😀');
  expect(await page.evaluate(() => pzLab.vfs.isDirectory('/Zomboid/empty-dir'))).toBe(true);
  expect(await page.evaluate(() => pzLab.vfs.lastModified('/Zomboid/java-io/nested/nio.txt'))).toBe(123456000);
});
