import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test('local game shaders translate, compile and link in WebGL2', async ({ page }, testInfo) => {
  await page.goto('/'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const results = await page.evaluate(async () => {
    const results = [];
    for (const name of ['default','blit','vboRenderer_PositionColorUV']) {
      const sources = await Promise.all(['vert','frag'].map(async suffix => {
        const response = await fetch(`/media/shaders/${name}.${suffix}`);
        if (!response.ok) throw new Error(`Local shader HTTP ${response.status}`);
        return response.text();
      }));
      const linked = pzLab.gl.desktopProgram(...sources);
      try {
        const hashes = await Promise.all(sources.map(async source => [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(source)))].map(b=>b.toString(16).padStart(2,'0')).join('')));
        results.push({name,hashes,linked:pzLab.gl.context.getProgramParameter(linked.program,pzLab.gl.context.LINK_STATUS),glError:pzLab.gl.getError()});
      } finally { pzLab.gl.context.deleteProgram(linked.program); }
    }
    return results;
  });
  expect(results.every(result=>result.linked && result.glError === 0)).toBe(true);
  await testInfo.attach('local-shader-metadata', {body:JSON.stringify(results,null,2),contentType:'application/json'});
});

test('actual media text is read by Java and a Java write survives reload', async ({ page }) => {
  const expected = JSON.parse(await readFile('build/real-browser/assets.json', 'utf8'));
  await page.goto('/'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const result = await page.evaluate(async ({ url }) => {
    await pzLab.preloadPacks(pzLab.vfs);
    const text = pzLab.teavm.exports.readJavaFile(url);
    const encoded = new TextEncoder().encode(text);
    const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', encoded))].map(x=>x.toString(16).padStart(2,'0')).join('');
    const written = pzLab.teavm.exports.exerciseJavaFiles('/Zomboid/real-copy-test');
    await pzLab.vfs.flush();
    return { hash, bytes:encoded.length, written };
  }, expected.text);
  expect(result.hash).toBe(expected.text.sha256); expect(result.bytes).toBe(expected.text.size);
  expect(result.written).toBe('java NIO: ñ😀');
  await page.reload(); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  expect(await page.evaluate(() => pzLab.teavm.exports.readJavaFile('/Zomboid/real-copy-test/nio.txt'))).toBe(result.written);
});

test('actual title PNG uploads to WebGL2 and every decoded RGBA byte matches', async ({ page }, testInfo) => {
  const expected = JSON.parse(await readFile('build/real-browser/assets.json', 'utf8')).png;
  await page.goto('/'); await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  const result = await page.evaluate(async ({ url }) => {
    const image = await pzLab.gl.loadTexture(url);
    try {
      const pixels = pzLab.gl.readTexture(image);
      const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', pixels))].map(x=>x.toString(16).padStart(2,'0')).join('');
      return { width:image.width, height:image.height, hash, glError:pzLab.gl.getError() };
    } finally { pzLab.gl.context.deleteTexture(image.texture); }
  }, expected);
  expect(result).toEqual({ width:expected.width, height:expected.height, hash:expected.rgba_sha256, glError:0 });
  await testInfo.attach('local-texture-metadata', { body:JSON.stringify(result), contentType:'application/json' });
});

test('game page reports a blocked build without substituting the platform demo', async ({ page }) => {
  const wasm = [];
  page.on('request', request => { if (request.url().endsWith('.wasm')) wasm.push(request.url()); });
  const response = await page.request.get('/game/build-state.json');
  expect([200,404]).toContain(response.status());
  // A fresh platform-only build has no game report yet. The independent
  // failure-response assertion below still runs in that configuration.
  if (response.ok()) {
    const actual = await response.json();
    expect(actual.mode).toBe('game-main');
    if (!actual.success) {
      await page.goto('/game.html');
      await expect(page.locator('#game-status')).toHaveText('Juego pendiente de integración');
      await expect(page.locator('#game-canvas')).toBeHidden();
      expect(wasm).toEqual([]);
      await page.screenshot({path:'test-results/game-blocked-task3.png',fullPage:true});
    }
  }
  await page.route('**/game/build-state.json', route => route.fulfill({ json:{ mode:'game-main', success:false } }));
  await page.goto('/game.html');
  await expect(page.locator('#game-status')).toHaveText('Juego pendiente de integración');
  expect(wasm).toEqual([]);
});
