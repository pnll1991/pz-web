import { test, expect } from '@playwright/test';

test('console preserves byte slices, UTF-8 boundaries and writes after repeated flush', async ({ page }) => {
  const messages=[];
  page.on('console',message=>{if(message.text().startsWith('ROUND5:'))messages.push({type:message.type(),text:message.text()});});
  await page.goto('/lab.html');await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await page.evaluate(async()=>{
    const module=await TeaVM.wasmGC.load('/regressions/console-utf8.wasm');
    module.exports.main([]);
  });
  expect(await page.evaluate(()=>pzConsoleUtf8Probe)).toBe('passed');
  expect(messages).toEqual([
    {type:'log',text:'ROUND5:'+'abcdefghijklmnopqrstuvwxyz'.repeat(4)+':ñ😀:end'},
    {type:'log',text:'ROUND5:ñ😀:split'},
    {type:'log',text:'ROUND5:after-flush'},
    {type:'error',text:'ROUND5:stderr-complete'}
  ]);
});
