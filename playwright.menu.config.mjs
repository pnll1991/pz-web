import { defineConfig } from '@playwright/test';
import base from './playwright.config.mjs';

// Real main menu test. Unlike playwright.game.config.mjs it does NOT run tests/prepare_real.py
// (that script overwrites build/real-browser/startup.txt and packs with a two-file fixture).
if (!process.env.PZ_HOME) throw new Error('PZ_HOME is required for the real menu test');
const port = process.env.PZ_MENU_TEST_PORT || '8098';
export default defineConfig({
  ...base, testMatch:['menu.spec.mjs'], testIgnore:[],
  outputDir:'test-results/menu',
  reporter:[['list'], ['json', { outputFile:'test-results/menu-results.json' }]],
  use:{ ...base.use, baseURL:`http://127.0.0.1:${port}`, viewport:{ width:1280, height:720 } },
  webServer:{ command:'node dev-server/server.mjs', url:`http://127.0.0.1:${port}/health`, reuseExistingServer:false,
    env:{ PORT:port, PZ_HOME:process.env.PZ_HOME, PZ_PACKS:'build/real-browser/packs' }, timeout:20_000 },
});
