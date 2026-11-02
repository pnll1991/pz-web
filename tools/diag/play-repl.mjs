// Round 86: long-lived two-client harness for the TEST instance (web 8199 + PZ server 16271). Loading the game takes ~7 min, so it is done once;
// afterwards tests are sent as files and run against the clients that stay in the world:
//   node tools/diag/play-repl.mjs --out /tmp/pz86/repl [--admin] [--only 1] [--keep]   (run in the background)
//   <out>/cmd.js     JS body, run (async) every time the file changes; it sees { a, b, clients, sleep, lua, shot, log }
//   <out>/out.txt    log: harness lines + every PZWEB-STATE line the clients print
//   <out>/quit       create it (or call process.exit in cmd.js) to close both browsers
// lua(c, code) mounts `code` into media/lua/client/zzz_pzweb_dbg.lua, which play.lua re-runs about once a second (?repl=1); say() reports back.
import { chromium } from '@playwright/test';
import { mkdirSync, appendFileSync, rmSync, readFileSync, existsSync, statSync, writeFileSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';
import { adminPassword } from './admin-pass.mjs';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i++) { const k = argv[i].replace(/^--/, ''); if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) A[k] = argv[++i]; else A[k] = true; }
const OUT = A.out || '/tmp/pz86/repl'; mkdirSync(OUT, { recursive: true });
const BASE = A.url || 'http://127.0.0.1:8199/play.html';
const PORT = A.port || 16271;
const names = A.only ? ['a'] : ['a', 'b'];
const t0 = Date.now(), sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (who, ...a) => { const s = `[+${Math.round((Date.now() - t0) / 1000)}s] ${who}: ${a.join(' ')}`; console.log(s); appendFileSync(`${OUT}/out.txt`, s + '\n'); };
if (!/127\.0\.0\.1:8199\//.test(BASE) && !A.anyurl) throw new Error('refusing to drive anything but the test web on 127.0.0.1:8199 (use --anyurl to override)');

let seq = 0;
async function open(name) {
  const c = { name, profile: `/tmp/pz86/profile-${name}`, ingame: false, errors: 0 };
  if (!A.keep) rmSync(c.profile, { recursive: true, force: true });
  c.context = await chromium.launchPersistentContext(c.profile, { ...launchOptions(), viewport: { width: 1280, height: 720 } });
  if (name === 'a' && A.admin) { // client "a" logs in as the server's admin account (password from the file mp-server.sh uses, see admin-pass.mjs)
    const pass = adminPassword();
    await c.context.addInitScript(p => { localStorage.setItem('pzweb.play.v1', JSON.stringify({ user: 'admin', pass: p })); }, pass);
  }
  c.page = c.context.pages()[0] || await c.context.newPage();
  c.page.on('console', m => {
    const s = m.text(), g = /PZWEB-STATE:(\S.*)$/.exec(s);
    if (g) { if (g[1] === 'ingame') c.ingame = true; log(name, g[1].slice(0, 1500)); }
    else if (A.console) appendFileSync(`${OUT}/${name}-console.txt`, s.slice(0, 600) + '\n');
  });
  c.page.on('pageerror', e => log(name, 'PAGEERROR', String(e.message).slice(0, 200)));
  c.page.on('crash', () => log(name, 'PAGE-CRASH'));
  c.shot = async n => { const f = `${OUT}/${name}-${n}.png`; await c.page.screenshot({ path: f, timeout: 90000 }); log(name, 'SHOT', f); };
  c.key = async (k, ms) => { await c.page.keyboard.down(k); await sleep(ms); await c.page.keyboard.up(k); };
  c.user = () => c.page.evaluate(() => JSON.parse(localStorage.getItem('pzweb.play.v1') || 'null')?.user).catch(() => null);
  return c;
}
/** Runs Lua inside the game of client c (next poll, ~1 s) and resolves when it reported dbg-done:<n>. */
async function lua(c, code, ms = 20000) {
  const n = ++seq;
  const wrapped = `if PZWEB_DBG_SEQ == ${n} then return end\nPZWEB_DBG_SEQ = ${n}\nlocal say = PZWEB_SAY\nlocal ok, err = pcall(function()\n${code}\nend)\nif not ok then say("dbg-error:" .. tostring(err)) end\nsay("dbg-done:${n}")\n`;
  let done = false;
  const onc = m => { if (m.text().endsWith(`PZWEB-STATE:dbg-done:${n}`)) done = true; };
  c.page.on('console', onc);
  await c.page.evaluate(src => pzRuntime.vfs.mount('/media/lua/client/zzz_pzweb_dbg.lua', new TextEncoder().encode(src)), wrapped);
  const end = Date.now() + ms;
  while (!done && Date.now() < end) await sleep(300);
  c.page.off('console', onc);
  await c.page.evaluate(() => pzRuntime.vfs.mount('/media/lua/client/zzz_pzweb_dbg.lua', new TextEncoder().encode('-- empty\n'))).catch(() => {});
  if (!done) log(c.name, 'lua-timeout', n);
  return done;
}

const clients = [];
try {
  for (const n of names) clients.push(await open(n));
  const [a, b] = clients;
  await Promise.all(clients.map(async c => {
    await c.page.goto(`${BASE}?port=${PORT}&repl=1${c.name === 'a' && A.admin ? '&auth=1' : ''}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    const end = Date.now() + 25 * 60000;
    while (!c.ingame && Date.now() < end) await sleep(2000);
    if (!c.ingame) throw new Error(`${c.name}: timeout waiting for ingame`);
    log(c.name, 'in the world as', await c.user());
  }));
  log('both', 'READY');
  let last = 0;
  const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
  const f = `${OUT}/cmd.js`;
  if (existsSync(f)) last = statSync(f).mtimeMs;
  while (!existsSync(`${OUT}/quit`)) {
    await sleep(1000);
    if (!existsSync(f)) continue;
    const m = statSync(f).mtimeMs;
    if (m === last) continue;
    last = m;
    const code = readFileSync(f, 'utf8');
    log('both', 'RUN cmd.js', `(${code.length} bytes)`);
    try { await new AsyncFunction('a', 'b', 'clients', 'sleep', 'lua', 'shot', 'log', code)(a, b, clients, sleep, lua, async n => { for (const c of clients) await c.shot(n); }, log); log('both', 'DONE cmd.js'); }
    catch (e) { log('both', 'CMD-ERROR', String(e?.stack || e).slice(0, 600)); }
  }
} catch (e) { log('both', 'FAIL', String(e).slice(0, 300)); process.exitCode = 1; }
for (const c of clients) { try { await c.context.close(); } catch {} }
if (!A.keep) for (const n of names) rmSync(`/tmp/pz86/profile-${n}`, { recursive: true, force: true });
log('both', 'CLOSED');
