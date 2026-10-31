import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';

// Round 78: the real PZ boot reaches TermsOfServiceState, a real mouse click on ACCEPT enters
// MainScreenState, and the Lua MainScreen (logo + button column) is drawn. Evidence is the
// screenshot test-results/menu-reached.png plus pixel statistics measured on it.
const count = (page, key) => page.evaluate(k => (globalThis.pzGameCounts || {})[k] || 0, key);
test('real main menu is drawn after accepting the terms of service', async ({ page }) => {
  test.setTimeout(840_000);
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.goto('/game.html', { waitUntil: 'domcontentloaded' });
  await expect.poll(() => count(page, 'zombie.gameStates.TermsOfServiceState#render()V:returned'),
    { timeout: 600_000, intervals: [5000] }).toBeGreaterThan(20);
  await page.waitForTimeout(15_000); // let ISTermsOfServiceUI finish building its children
  // ACCEPT centre (PZ hit-test is strict x > ui.x, so click the middle, not the edge).
  await page.mouse.move(660, 515); await page.waitForTimeout(300);
  await page.mouse.down(); await page.waitForTimeout(120); await page.mouse.up();
  await expect.poll(() => count(page, 'zombie.gameStates.MainScreenState#enter()V:returned'),
    { timeout: 180_000, intervals: [2000] }).toBe(1);
  await expect.poll(() => count(page, 'zombie.gameStates.MainScreenState#render()V:returned'),
    { timeout: 180_000, intervals: [2000] }).toBeGreaterThan(60);
  mkdirSync('test-results', { recursive: true });
  const shot = 'test-results/menu-reached.png';
  // The logo fades in over a few seconds (MainScreen alpha ramp), so re-measure for up to 2 min.
  let stats;
  for (let i = 0; i < 24; i++) {
    await page.screenshot({ path: shot, timeout: 60_000 });
    stats = measure(shot);
    if (stats.logo > 0.02 && stats.buttons > 0.005) break;
    await page.waitForTimeout(5000);
  }
  writeFileSync('test-results/menu-reached.json', JSON.stringify({ stats, pageErrors }, null, 2) + '\n');
  expect(pageErrors).toEqual([]);
  expect(stats.colors).toBeGreaterThan(10_000);   // the real Title background, not a flat clear colour
  expect(stats.logo).toBeGreaterThan(0.02);       // logo drawn top-left
  expect(stats.buttons).toBeGreaterThan(0.005);   // menu button labels in the left column
});

// Fraction of light pixels (min channel > 170) in the logo box and in the button column.
function measure(shot) {
  return JSON.parse(execFileSync('python3', ['-c', `
import json,sys
from PIL import Image
im=Image.open(sys.argv[1]).convert('RGB');px=im.load()
def frac(x0,y0,x1,y1):
  n=t=0
  for y in range(y0,y1,2):
    for x in range(x0,x1,2):
      t+=1;n+=min(px[x,y])>170
  return n/t
print(json.dumps({'size':im.size,'colors':len(im.getcolors(1<<22)),'logo':frac(80,40,360,240),'buttons':frac(120,280,280,680),'middle':frac(500,100,900,380)}))`, shot], { encoding: 'utf8' }));
}
