import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

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
  await page.route('**/game/build-state.json', route => route.fulfill({ json:{ mode:'game-main', success:false } }));
  await page.goto('/game.html');
  await expect(page.locator('#game-status')).toHaveText('Juego pendiente de integración');
  expect(wasm).toEqual([]);
});
