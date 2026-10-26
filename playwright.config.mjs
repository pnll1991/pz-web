import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir:'tests/browser', timeout:30_000, workers:1, fullyParallel:false,
  reporter:[['list'],['json',{outputFile:'test-results/browser-results.json'}]],
  use:{baseURL:'http://127.0.0.1:8090',viewport:{width:1365,height:1100},
    launchOptions:{executablePath:process.env.CHROME_PATH || '/usr/bin/google-chrome',
      args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage']},
    screenshot:'only-on-failure',trace:'retain-on-failure'},
  webServer:{command:'python3 tests/prepare_browser.py && node dev-server/server.mjs',url:'http://127.0.0.1:8090/health',reuseExistingServer:false,
    env:{PZ_HOME:'build/browser-fixture/game',PZ_PACKS:'build/browser-fixture/packs'},timeout:20_000},
});
