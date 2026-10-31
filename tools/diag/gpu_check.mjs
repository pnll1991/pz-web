// Ronda 80: comprueba qué GPU usa el Chrome de pruebas.
// Uso: node tools/diag/gpu_check.mjs   (PZ_GPU=nvidia|swiftshader; PZ_CHROME_ARGS='["--flag",...]' reemplaza
// los args del perfil para experimentar; PZ_CHROME_HEADED=1 abre ventana). Envolver en flock chrome.lock.
// Imprime UNMASKED_VENDOR/RENDERER de WebGL2, MAX_TEXTURE_SIZE y un número de throughput de dibujo.
import { chromium } from '@playwright/test';
import { rmSync } from 'node:fs';
import { launchOptions, userDataDir, profileName } from './chrome-flags.mjs';

const opts = launchOptions();
if (process.env.PZ_CHROME_ARGS) opts.args = ['--disable-dev-shm-usage', ...JSON.parse(process.env.PZ_CHROME_ARGS)];
const dir = userDataDir('pzgpu');
const ctx = await chromium.launchPersistentContext(dir, { ...opts, viewport: { width: 640, height: 480 } });
try {
  const page = ctx.pages()[0] || await ctx.newPage();
  await page.goto('data:text/html,<canvas id=c width=1024 height=1024></canvas>');
  const r = await page.evaluate(() => {
    const gl = document.getElementById('c').getContext('webgl2', { antialias: false });
    if (!gl) return { error: 'sin WebGL2' };
    const ext = gl.getExtension('WEBGL_debug_renderer_info');
    const info = {
      vendor: ext ? gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR),
      renderer: ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER),
      maxTex: gl.getParameter(gl.MAX_TEXTURE_SIZE),
    };
    const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o); return o; };
    const p = gl.createProgram();
    gl.attachShader(p, sh(gl.VERTEX_SHADER, '#version 300 es\nin vec2 a;void main(){gl_Position=vec4(a,0.,1.);}'));
    gl.attachShader(p, sh(gl.FRAGMENT_SHADER, '#version 300 es\nprecision highp float;uniform float k;out vec4 o;' +
      'void main(){vec2 u=gl_FragCoord.xy*0.001;float s=k;for(int i=0;i<32;i++)s=fract(s*1.37+sin(u.x*float(i))+u.y);o=vec4(s,u,1.);}'));
    gl.linkProgram(p); gl.useProgram(p);
    const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE);
    const px = new Uint8Array(4), k = gl.getUniformLocation(p, 'k');
    const run = n => { const t = performance.now(); for (let i = 0; i < n; i++) { gl.uniform1f(k, i); gl.drawArrays(gl.TRIANGLES, 0, 3); }
      gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px); return performance.now() - t; };
    run(4);
    let N = 200, ms = run(N);
    if (ms < 100) { N = 5000; ms = run(N); } // GPU real: más dibujos para que el tiempo sea medible
    info.draws = N; info.ms = +ms.toFixed(1);
    info.mpixPerSec = +((N * 1024 * 1024) / (ms / 1000) / 1e6).toFixed(0); // pantallas completas 1024² con 32 iteraciones ALU
    return info;
  });
  console.log(JSON.stringify({ profile: process.env.PZ_CHROME_ARGS ? 'custom' : profileName(), headless: opts.headless, args: opts.args, ...r }));
} finally {
  await ctx.close();
  if (!process.env.PZ_CHROME_USERDIR) rmSync(dir, { recursive: true, force: true });
}
