// Ronda 79: interactive driver. Loads game.html in headless Chrome and executes commands
// appended to $PZ_DRIVE/cmd.txt, one per line:
//   click X Y | move X Y | key KEY [ms] | type TEXT | wait MS | shot NAME | eval JS | counts REGEX
//   until KEYSUBSTR MIN TIMEOUT_MS (waits for pzGameCounts)
// Output goes to $PZ_DRIVE/out.txt; console to $PZ_DRIVE/console.txt; shots to ~/pzshots/NAME.png.
import { chromium } from '@playwright/test';
import { writeFileSync, appendFileSync, readFileSync, existsSync, mkdirSync } from 'node:fs';
const PORT = process.env.PZ_DIAG_PORT || '8098';
const D = process.env.PZ_DRIVE || '/tmp/pzdrive'; mkdirSync(D, { recursive: true });
const SHOTS = `${process.env.HOME}/pzshots`;
writeFileSync(`${D}/cmd.txt`, ''); writeFileSync(`${D}/out.txt`, ''); writeFileSync(`${D}/console.txt`, '');
const t0 = Date.now();
const log = (...a) => appendFileSync(`${D}/out.txt`, `[${Math.round((Date.now() - t0) / 1000)}s] ` + a.map(String).join(' ') + '\n');
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-dev-shm-usage'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
page.on('console', m => appendFileSync(`${D}/console.txt`, m.text().slice(0, 2000) + '\n'));
page.on('pageerror', e => log('PAGEERROR', String(e.stack || e.message).slice(0, 1500).replace(/\n/g, ' | ')));
await page.goto(`http://127.0.0.1:${PORT}/game.html?maplog=1`, { waitUntil: 'domcontentloaded' });
log('LOADED');
let done = 0;
for (;;) {
  await page.waitForTimeout(1000);
  const lines = readFileSync(`${D}/cmd.txt`, 'utf8').split('\n').filter(Boolean);
  while (done < lines.length) {
    const line = lines[done++]; const [c, ...a] = line.split(' ');
    try {
      if (c === 'click') { await page.mouse.move(+a[0], +a[1]); await page.waitForTimeout(300); await page.mouse.down(); await page.waitForTimeout(120); await page.mouse.up(); }
      else if (c === 'move') await page.mouse.move(+a[0], +a[1]);
      else if (c === 'key') { await page.keyboard.down(a[0]); await page.waitForTimeout(+(a[1] || 120)); await page.keyboard.up(a[0]); }
      else if (c === 'type') await page.keyboard.type(a.join(' '), { delay: 80 });
      else if (c === 'wait') await page.waitForTimeout(+a[0]);
      else if (c === 'shot') await page.screenshot({ path: `${SHOTS}/${a[0]}.png`, timeout: 90000 });
      else if (c === 'eval') log('EVAL', String(JSON.stringify(await page.evaluate(a.join(' ')))).slice(0, 6000));
      else if (c === 'counts') { const re = a.join(' '); log('COUNTS', JSON.stringify(await page.evaluate(r => Object.fromEntries(Object.entries(globalThis.pzGameCounts || {}).filter(([k]) => !k.endsWith('#last') && new RegExp(r).test(k))), re)).slice(0, 6000)); }
      else if (c === 'until') { // until KEY_SUBSTRING MINCOUNT TIMEOUT_MS
        const end = Date.now() + +a[2]; let n = 0;
        while (Date.now() < end) { n = await page.evaluate(k => Object.entries(globalThis.pzGameCounts || {}).filter(([x]) => x.includes(k) && x.endsWith(':returned')).reduce((s, [, v]) => s + v, 0), a[0]).catch(() => 0); if (n >= +a[1]) break; await page.waitForTimeout(3000); }
        log('UNTIL', a[0], n);
      }
      else if (c === 'quit') { await browser.close(); process.exit(0); }
      log('OK', line);
    } catch (e) { log('FAIL', line, String(e).slice(0, 300)); }
  }
}
