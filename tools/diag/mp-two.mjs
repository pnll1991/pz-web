// Round 82: two real browser clients (two Chrome profiles under /tmp) join the local dedicated server, create a character
// each, and move so each one can be seen on the other's screen. Prerequisite per profile: the server entry plus the account
// already saved in the multiplayer list (see docs/multiplayer-design.md). Screenshots go to --out (not versioned).
//   node tools/diag/mp-two.mjs --out DIR [--profile-a P] [--profile-b P] [--page game.html] [--query "texcache=1&persist=1"] [--new-char]
import { chromium } from '@playwright/test';
import { mkdirSync, appendFileSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i++) { const k = argv[i].replace(/^--/, ''); if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) A[k] = argv[++i]; else A[k] = true; }
const OUT = A.out || '/tmp/pz82/two-shots'; mkdirSync(OUT, { recursive: true });
const URL_ = `http://127.0.0.1:8098/${A.page || 'game.html'}?${A.query || 'texcache=1&persist=1'}`;
const clients = [
  { name: 'alice', profile: A['profile-a'] || '/tmp/pz81/profile', connectY: 223 },
  { name: 'bob', profile: A['profile-b'] || '/tmp/pz81/profile-b', connectY: 291 },
];
const t0 = Date.now(), sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (who, ...a) => { const s = `[+${Math.round((Date.now() - t0) / 1000)}s] ${who}: ${a.join(' ')}`; console.log(s); appendFileSync(`${OUT}/log.txt`, s + '\n'); };
const TOS = 'zombie.gameStates.TermsOfServiceState#render()V:returned', MENU = 'zombie.gameStates.MainScreenState#render()V:returned';

async function open(c) {
  c.context = await chromium.launchPersistentContext(c.profile, { ...launchOptions(), viewport: { width: 1280, height: 720 } });
  c.page = c.context.pages()[0] || await c.context.newPage();
  c.loaded = false;
  c.page.on('console', m => { const s = m.text(); if (/game loading took/.test(s)) c.loaded = true; if (A.console) appendFileSync(`${OUT}/${c.name}-console.txt`, s.slice(0, 1500) + '\n'); });
  c.page.on('pageerror', e => log(c.name, 'PAGEERROR', String(e.message).slice(0, 200)));
  c.page.on('crash', () => log(c.name, 'PAGE-CRASH'));
  c.click = async (x, y) => { await c.page.mouse.move(x, y); await sleep(300); await c.page.mouse.down(); await sleep(120); await c.page.mouse.up(); };
  c.key = async (k, ms) => { await c.page.keyboard.down(k); await sleep(ms); await c.page.keyboard.up(k); };
  c.shot = async n => { await c.page.screenshot({ path: `${OUT}/${c.name}-${n}.png`, timeout: 90000 }); log(c.name, 'SHOT', n); };
  c.count = label => c.page.evaluate(l => (globalThis.pzGameCounts || {})[l] | 0, label).catch(() => 0);
}
async function until(c, what, pred, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) { if (await pred()) return; await sleep(2000); }
  throw new Error(`${c.name}: timeout waiting for ${what}`);
}
async function toMenu(c) {
  await c.page.goto(URL_, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await until(c, 'ToS', async () => (await c.count(TOS)) >= 20, 25 * 60000);
  await c.click(610, 515); // ACCEPT
  await until(c, 'menu', async () => (await c.count(MENU)) >= 100, 15 * 60000);
  await sleep(3000); log(c.name, 'menu ready');
}
async function join(c) {
  await c.click(180, 385); await sleep(6000);       // MULTIPLAYER
  await c.click(906, c.connectY); await sleep(2000); // CONNECT on the saved account
  log(c.name, 'connect clicked');
  if (A['new-char']) { // spawn region -> character -> PLAY: the same corner button each time
    await sleep(25000);
    for (let i = 0; i < 3; i++) { await c.click(1226, 696); await sleep(i < 2 ? 7000 : 2000); }
  }
  await until(c, 'game loading', async () => c.loaded, 15 * 60000);
  log(c.name, 'world loaded; waiting for Click to Start');
  for (let i = 0; i < 12; i++) {
    await sleep(8000);
    const size = (await c.page.screenshot({ timeout: 90000 })).length;
    if (size > 40000) break; // the Click-to-Start / loading screens are almost black
    await c.click(640, 400);
  }
  await sleep(5000);
  await c.click(1182, 674); await sleep(3000); // CLOSE the survival guide
  await c.shot('1-joined');
}

try {
  for (const c of clients) await open(c);
  await Promise.all(clients.map(async c => { await toMenu(c); await join(c); }));
  const [a, b] = clients;
  await sleep(10000);
  await a.shot('2-before'); await b.shot('2-before');
  // short, separate steps so the two stay in each other's view: bob walks while alice stands still, then the reverse
  await b.key('KeyD', 900); await sleep(2500);
  await a.shot('3-bob-walked-right'); await b.shot('3-bob-walked-right');
  await b.key('KeyS', 900); await sleep(2500);
  await a.shot('4-bob-walked-down'); await b.shot('4-bob-walked-down');
  await a.key('KeyW', 1500); await sleep(2500);
  await a.shot('5-alice-walked-up'); await b.shot('5-alice-walked-up');
  log('both', 'OK');
} catch (e) { log('both', 'FAIL', String(e).slice(0, 300)); process.exitCode = 1; }
for (const c of clients) { try { await c.context?.close(); } catch {} }
