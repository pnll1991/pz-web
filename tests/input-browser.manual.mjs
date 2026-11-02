// Prueba manual de controles en navegadores reales (no corre en la CI). Carga port/runtime/input.mjs y window.mjs en una página sintética
// (canvas + registro visible) y la maneja con eventos reales del navegador: clic derecho, rueda, teclas, foco y pointer lock.
//   Chrome:  node tests/input-browser.manual.mjs chrome            (~/.local/bin/pz-chrome-slot node tests/input-browser.manual.mjs chrome; CHROME_PATH=/usr/bin/google-chrome)
//   Firefox: node tests/input-browser.manual.mjs firefox           (FIREFOX_BIN y GECKODRIVER_BIN: Firefox portable fuera del repo; WebDriver clásico)
// Salida: test-results/input/<navegador>.json y <navegador>-*.png (ignorados por git). Sale con 1 si algún chequeo falla.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const which = process.argv[2] ?? 'chrome';
const out = path.join(root, 'test-results/input'); fs.mkdirSync(out, { recursive: true });

const PAGE = `<!doctype html><meta charset=utf-8><title>input</title>
<style>html,body{margin:0;overflow:hidden;background:#222;color:#cfc;font:13px monospace}#c{position:fixed;left:0;top:0;width:640px;height:360px;background:#345;outline:none;touch-action:none;user-select:none}
#hud{position:fixed;left:0;top:380px;width:640px;height:80px;background:#432}#log{position:fixed;left:660px;top:0;white-space:pre;width:600px}</style>
<canvas id=c width=640 height=360 tabindex=0></canvas><div id=hud>HUD (no es el canvas)</div><pre id=log></pre>
<script type=module>
import { CanvasInput } from '/runtime/input.mjs'; import { BrowserWindow } from '/runtime/window.mjs';
const c = document.getElementById('c'), input = new CanvasInput(c), win = new BrowserWindow(c, {}, input); win.initialized = true; win.created = true;
const seen = { contextmenu: [], blur: 0, focus: 0 };
// Listeners added after CanvasInput's: they see the final defaultPrevented.
window.addEventListener('contextmenu', e => seen.contextmenu.push({ target: e.target.id || e.target.tagName, prevented: e.defaultPrevented }));
window.addEventListener('blur', () => seen.blur++); window.addEventListener('focus', () => seen.focus++);
c.focus();
const log = document.getElementById('log');
setInterval(() => { log.textContent = JSON.stringify({ focused: win.attrib(1, 131073), active: document.activeElement?.id, lock: win.pointerLock, held: [...input.buttons], keys: [...input.keys], seen, last: input.events.slice(-8) }, null, 1); }, 100);
window.pz = { input, win, seen, drain: () => input.drain(), setDisabled: () => win.setInputMode(1, 208897, 212995), setNormal: () => win.setInputMode(1, 208897, 212993),
  state: () => ({ active: document.activeElement?.id ?? document.activeElement?.tagName, locked: document.pointerLockElement === c, pointerLock: win.pointerLock, focused: win.attrib(1, 131073), buttons: [...input.buttons], keys: [...input.keys], seen: JSON.parse(JSON.stringify(seen)), error: win.pointerLockError }) };
</script>`;

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/') { res.setHeader('content-type', 'text/html'); return res.end(PAGE); }
  const file = path.join(root, 'port', url.pathname);
  if (url.pathname.startsWith('/runtime/') && fs.existsSync(file)) { res.setHeader('content-type', 'text/javascript'); return res.end(fs.readFileSync(file)); }
  res.statusCode = 404; res.end();
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}/`;

const results = [];
const check = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}${detail === undefined ? '' : ' ' + JSON.stringify(detail)}`); };

// El driver: la misma superficie para Chrome (Playwright) y Firefox (WebDriver).
async function chromeDriver() {
  const { chromium } = await import('playwright-core');
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome', headless: true,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-dev-shm-usage'] });
  const context = await browser.newContext({ viewport: { width: 1280, height: 560 } });
  const page = await context.newPage();
  await page.goto(base);
  const cdp = await context.newCDPSession(page);
  return {
    name: 'chrome ' + browser.version(),
    eval: (fn) => page.evaluate(fn),
    click: async (x, y, button = 'left') => { await page.mouse.move(x, y); await page.mouse.down({ button }); await page.mouse.up({ button }); },
    move: (x, y) => page.mouse.move(x, y),
    wheel: (dy) => page.mouse.wheel(0, dy),
    key: (k) => page.keyboard.press(k),
    // Un alt-tab real no se puede inyectar: se abre otra pestaña al frente y se vuelve; si el navegador no lo anuncia con blur, queda anotado.
    leaveAndReturn: async () => { const other = await context.newPage(); await other.goto('about:blank'); await other.bringToFront(); await new Promise(r => setTimeout(r, 400)); await other.close(); await page.bringToFront(); await new Promise(r => setTimeout(r, 400)); },
    focusLossEmulation: async (on) => { await cdp.send('Emulation.setFocusEmulationEnabled', { enabled: !on }); await new Promise(r => setTimeout(r, 300)); },
    shot: (n) => page.screenshot({ path: path.join(out, `chrome-${n}.png`) }),
    close: () => browser.close(),
  };
}

async function firefoxDriver() {
  const gecko = process.env.GECKODRIVER_BIN || '/tmp/pz-fx/geckodriver', bin = process.env.FIREFOX_BIN || '/tmp/pz-fx/firefox/firefox';
  const proc = spawn(gecko, ['--port', '4455', '--binary', bin], { stdio: 'ignore' });
  const call = async (method, p, body) => { const r = await fetch(`http://127.0.0.1:4455${p}`, { method, headers: { 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) }); const j = await r.json(); if (j.value?.error) throw new Error(`${j.value.error}: ${j.value.message}`); return j.value; };
  for (let i = 0; i < 50; i++) { try { await fetch('http://127.0.0.1:4455/status'); break; } catch { await new Promise(r => setTimeout(r, 100)); } }
  const s = await call('POST', '/session', { capabilities: { alwaysMatch: { browserName: 'firefox', 'moz:firefoxOptions': { args: ['-headless', '-width', '1280', '-height', '700'] } } } });
  const sid = s.sessionId, S = p => `/session/${sid}${p}`;
  await call('POST', S('/url'), { url: base });
  const wait = ms => new Promise(r => setTimeout(r, ms));
  const actions = (id, type, acts) => call('POST', S('/actions'), { actions: [{ type, id, ...(type === 'pointer' ? { parameters: { pointerType: 'mouse' } } : {}), actions: acts }] });
  const btn = { left: 0, middle: 1, right: 2 };
  return {
    name: 'firefox ' + s.capabilities.browserVersion,
    eval: (fn) => call('POST', S('/execute/sync'), { script: `return (${fn.toString()})()`, args: [] }),
    click: (x, y, button = 'left') => actions('m', 'pointer', [{ type: 'pointerMove', x, y, origin: 'viewport' }, { type: 'pointerDown', button: btn[button] }, { type: 'pointerUp', button: btn[button] }]),
    move: (x, y) => actions('m', 'pointer', [{ type: 'pointerMove', x, y, origin: 'viewport' }]),
    wheel: (dy) => call('POST', S('/actions'), { actions: [{ type: 'wheel', id: 'w', actions: [{ type: 'scroll', x: 100, y: 100, deltaX: 0, deltaY: dy, origin: 'viewport' }] }] }),
    key: (k) => { const v = { Tab: '', Escape: '', Space: ' ' }[k] ?? k; return actions('k', 'key', [{ type: 'keyDown', value: v }, { type: 'keyUp', value: v }]); },
    leaveAndReturn: async () => { const main = await call('GET', S('/window')); await call('POST', S('/window/new'), { type: 'tab' }).then(w => call('POST', S('/window'), { handle: w.handle })); await wait(400); await call('POST', S('/window'), { handle: main }); await wait(400); },
    focusLossEmulation: async () => {},
    shot: async (n) => fs.writeFileSync(path.join(out, `firefox-${n}.png`), Buffer.from(await call('GET', S('/screenshot')), 'base64')),
    close: async () => { try { await call('DELETE', S('')); } catch {} proc.kill(); },
  };
}

const d = await (which === 'firefox' ? firefoxDriver() : chromeDriver());
const wait = ms => new Promise(r => setTimeout(r, ms));
const st = () => d.eval(() => window.pz.state());
const drain = () => d.eval(() => window.pz.drain());
console.log('Navegador:', d.name);
try {
  await wait(300); await drain();
  // 1. Clic derecho: sin menú del navegador (contextmenu cancelado) y el botón 1 de GLFW llega al juego.
  await d.click(300, 200, 'right'); await wait(200);
  let s = await st(), ev = await drain();
  const rc = s.seen.contextmenu.at(-1);
  check('clic derecho sobre el canvas: contextmenu cancelado', rc?.prevented === true && rc.target === 'c', s.seen.contextmenu);
  check('clic derecho: botón 1 (derecho GLFW) baja y sube', JSON.stringify(ev.filter(e => e.type === 'button').map(e => `${e.button}:${e.action}`)) === '["1:1","1:0"]', ev.filter(e => e.type === 'button'));
  await d.shot('1-clic-derecho');
  await d.click(300, 420, 'right'); await wait(200); s = await st();
  check('clic derecho sobre el HUD (fuera del canvas): contextmenu cancelado', s.seen.contextmenu.at(-1)?.prevented === true, s.seen.contextmenu.at(-1));
  // 2. Botones izquierdo y central.
  await drain(); await d.click(300, 200, 'left'); await d.click(300, 200, 'middle'); await wait(200); ev = await drain();
  check('izquierdo = 0 y central = 2', JSON.stringify(ev.filter(e => e.type === 'button').map(e => `${e.button}:${e.action}`)) === '["0:1","0:0","2:1","2:0"]', ev.filter(e => e.type === 'button'));
  // 3. Rueda: hacia abajo es negativa y la página no se desplaza.
  await drain(); await d.move(300, 200); await d.wheel(100); await wait(200); ev = await drain();
  const sc = ev.filter(e => e.type === 'scroll');
  check('rueda hacia abajo -> scroll negativo', sc.length > 0 && sc.every(e => e.y < 0), sc);
  // 4. Teclado: W, Space y Tab llegan; Tab no mueve el foco del canvas.
  await drain(); for (const k of ['w', 'Space', 'Tab']) await d.key(k); await wait(200); ev = await drain(); s = await st();
  check('teclas W (87), Space (32) y Tab (258) con down y up', [87, 32, 258].every(k => [0, 1].every(a => ev.some(e => e.type === 'key' && e.key === k && e.action === a))), ev.filter(e => e.type === 'key').map(e => `${e.key}:${e.action}`));
  check('Tab no saca el foco del canvas', s.active === 'c', s.active);
  // 5. Foco: salir de la pestaña suelta lo apretado y al volver el canvas recupera el foco y el juego ve GLFW_FOCUSED=1.
  await drain(); await d.eval(() => { window.pz.input.keys.add(87); window.pz.input.buttons.add(0); }); // algo apretado al irse
  await d.leaveAndReturn(); s = await st(); ev = await drain();
  const blurred = s.seen.blur > 0;
  console.log('   (el navegador anunció blur al cambiar de pestaña:', blurred, ')');
  if (blurred) check('al perder el foco se sueltan teclas y botones', s.keys.length === 0 && s.buttons.length === 0 && ev.some(e => e.type === 'key' && e.key === 87 && e.action === 0), { keys: s.keys, buttons: s.buttons });
  check('al volver, el canvas tiene el foco y GLFW_FOCUSED=1', s.active === 'c' && s.focused === 1, s);
  await d.eval(() => document.activeElement.blur?.()); await d.eval(() => window.dispatchEvent(new Event('focus'))); s = await st();
  check('focus de la ventana devuelve el foco al canvas', s.active === 'c', s.active);
  await d.key('w'); await wait(100); ev = await drain();
  check('las teclas siguen llegando después de volver', ev.some(e => e.type === 'key' && e.key === 87 && e.action === 1), ev.filter(e => e.type === 'key').length);
  await d.shot('2-foco');
  // 6. Pointer lock: el juego pide DISABLED; sin gesto el navegador lo rechaza, con un clic lo concede.
  await d.eval(() => window.pz.setDisabled()); await wait(300); s = await st();
  // Chrome exige un gesto; Firefox lo concede si la página ya tuvo activación. Lo que se exige: win.pointerLock refleja lo que el navegador decidió.
  check('pointer lock pedido por el juego: win.pointerLock refleja el estado real del navegador', s.pointerLock === s.locked, { locked: s.locked, error: s.error });
  if (s.locked) { await d.eval(() => document.exitPointerLock()); await wait(1500); }
  await d.click(300, 200, 'left'); await wait(500); s = await st();
  check('el clic en el canvas concede el pointer lock', s.locked === true && s.pointerLock === true, s);
  await drain(); await d.move(310, 205); await d.move(330, 215); await wait(200); ev = await drain();
  const cur = ev.filter(e => e.type === 'cursor'); const lastCur = cur.at(-1);
  check('con lock, el cursor virtual avanza por los deltas', cur.length > 0 && lastCur.x !== 300, cur.slice(-2));
  await d.shot('3-pointer-lock');
  await d.key('Escape'); await wait(500); s = await st();
  let escOk = s.locked === false;
  if (!escOk) { await d.eval(() => document.exitPointerLock()); await wait(500); s = await st(); }
  console.log('   (Escape sacó el lock:', escOk, '; si no, se usó document.exitPointerLock)');
  check('al salir del lock, win.pointerLock=false, sin botones apretados y el modo sigue DISABLED', s.locked === false && s.pointerLock === false && s.buttons.length === 0, s);
  await wait(1500); // Chrome rechaza un lock nuevo durante ~1,25 s después de Esc
  await d.click(300, 200, 'left'); await wait(500); s = await st();
  check('el siguiente clic vuelve a tomar el lock', s.locked === true && s.pointerLock === true, s);
  await d.eval(() => window.pz.setNormal()); await wait(300); s = await st();
  check('volver al cursor normal suelta el lock', s.locked === false, s);
} finally { await d.close(); server.close(); }
fs.writeFileSync(path.join(out, `${which}.json`), JSON.stringify({ browser: d.name, results }, null, 1));
const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} chequeos OK en ${d.name}`);
process.exit(failed.length ? 1 : 0);
