// Round 83: two FRESH browsers (empty profiles under /tmp = no saved account, no cache) open the same /play.html URL.
// Each must enter the shared world on its own (no menu, no form), and each must see the other. Screenshots go to --out.
//   node tools/diag/play-two.mjs --out DIR [--page play.html] [--only 1]   (--only 1 = a single client, for a quick check)
import { chromium } from '@playwright/test';
import { mkdirSync, appendFileSync, rmSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i++) { const k = argv[i].replace(/^--/, ''); if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) A[k] = argv[++i]; else A[k] = true; }
const OUT = A.out || '/tmp/pz83/two-shots'; mkdirSync(OUT, { recursive: true });
const URL_ = `http://127.0.0.1:8098/${A.page || 'play.html'}`;
const names = A.only ? ['a'] : ['a', 'b'];
const t0 = Date.now(), sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (who, ...a) => { const s = `[+${Math.round((Date.now() - t0) / 1000)}s] ${who}: ${a.join(' ')}`; console.log(s); appendFileSync(`${OUT}/log.txt`, s + '\n'); };

async function open(name) {
  const c = { name, profile: `/tmp/pz83/profile-${name}`, states: [], ingame: false };
  if (!A.keep) rmSync(c.profile, { recursive: true, force: true });
  c.context = await chromium.launchPersistentContext(c.profile, { ...launchOptions(), viewport: { width: 1280, height: 720 } });
  c.page = c.context.pages()[0] || await c.context.newPage();
  c.page.on('console', m => {
    const s = m.text(), g = /PZWEB-STATE:(\S.*)$/.exec(s);
    if (g) { c.states.push(g[1]); if (g[1] === 'ingame') c.ingame = true; log(name, 'state', g[1]); }
    if (A.console) appendFileSync(`${OUT}/${name}-console.txt`, s.slice(0, 1500) + '\n');
  });
  c.page.on('pageerror', e => log(name, 'PAGEERROR', String(e.message).slice(0, 200)));
  c.page.on('crash', () => log(name, 'PAGE-CRASH'));
  c.key = async (k, ms) => { await c.page.keyboard.down(k); await sleep(ms); await c.page.keyboard.up(k); };
  c.shot = async n => { await c.page.screenshot({ path: `${OUT}/${name}-${n}.png`, timeout: 90000 }); log(name, 'SHOT', n); };
  c.user = () => c.page.evaluate(() => JSON.parse(localStorage.getItem('pzweb.play.v1') || 'null')?.user).catch(() => null);
  return c;
}
async function until(c, what, pred, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) { if (await pred()) return; await sleep(2000); }
  throw new Error(`${c.name}: timeout waiting for ${what}`);
}

const clients = [];
try {
  for (const n of names) clients.push(await open(n));
  await Promise.all(clients.map(async c => {
    await c.page.goto(URL_, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await until(c, 'ingame', async () => c.ingame, 25 * 60000);
    log(c.name, 'in the world as', await c.user());
  }));
  await sleep(8000);
  for (const c of clients) await c.shot('1-joined');
  if (clients.length === 2) {
    const [a, b] = clients;
    await b.key('KeyD', 900); await sleep(2500);
    await a.shot('2-b-walked'); await b.shot('2-b-walked');
    await a.key('KeyA', 900); await sleep(2500);
    await a.shot('3-a-walked'); await b.shot('3-a-walked');
    log('both', 'users', await a.user(), await b.user());
  }
  log('both', 'OK');
} catch (e) { log('both', 'FAIL', String(e).slice(0, 300)); process.exitCode = 1; }
for (const c of clients) { try { await c.context.close(); } catch {} }
