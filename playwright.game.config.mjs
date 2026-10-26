import { defineConfig } from '@playwright/test';
import base from './playwright.config.mjs';

if (!process.env.PZ_HOME) throw new Error('PZ_HOME is required for real-copy tests');
const port = process.env.PZ_REAL_TEST_PORT || '8092';
export default defineConfig({
  ...base, testMatch:'real-game.spec.mjs', testIgnore:[],
  reporter:[['list'], ['json', { outputFile:'test-results/real-game-results.json' }]],
  use:{ ...base.use, baseURL:`http://127.0.0.1:${port}` },
  webServer:{ command:'python3 tests/prepare_real.py && node dev-server/server.mjs',
    url:`http://127.0.0.1:${port}/health`, reuseExistingServer:false,
    env:{ PORT:port, PZ_HOME:process.env.PZ_HOME, PZ_PACKS:'build/real-browser/packs' }, timeout:20_000 },
});
