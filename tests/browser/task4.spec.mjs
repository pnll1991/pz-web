import { test, expect } from '@playwright/test';

test('task 4 standard Java APIs execute and SQLite persists in Chrome', async ({ page }, testInfo) => {
  const messages=[],errors=[]; page.on('console',message=>{messages.push(message.text());if(message.type()==='error')errors.push(message.text());}); page.on('pageerror',error=>{messages.push(error.message);errors.push(error.message);});
  try {
    await page.goto('/lab.html');await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
    await page.evaluate(()=>pzLab.teavm.exports.checkTask4());
    await expect.poll(()=>page.evaluate(()=>pzTask4?.status),{timeout:20000}).not.toBe('running');
    expect(await page.evaluate(()=>pzTask4)).toEqual({status:'passed',detail:'memory,handles,concurrency,resources,xml,watchers,jdbc,digest'});
    await page.reload();await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
    await page.evaluate(()=>pzLab.teavm.exports.checkSavedSql());
    await expect.poll(()=>page.evaluate(()=>pzTask4)).toEqual({status:'passed',detail:'ñ😀:9223372036854775807'});
    await page.screenshot({path:'test-results/task4-platform.png',fullPage:true});
    expect(errors).toEqual([]);
  } finally {await testInfo.attach('task4-console',{body:messages.join('\n'),contentType:'text/plain'});}
});

test('compiler root metadata regression executes in Chrome', async ({page})=>{
  await page.goto('/lab.html');await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await page.evaluate(async()=>{const runtime=await TeaVM.wasmGC.load('/regressions/object-metadata.wasm');runtime.exports.main([]);});
  await expect.poll(()=>page.evaluate(()=>globalThis.pzCompilerProbe)).toBe('java.lang.Object:port.CompilerRegressionTest$Orphan');
});

test('reflected numeric intrinsics execute in Chrome',async({page})=>{
  await page.goto('/lab.html');await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await page.evaluate(async()=>{globalThis.pzIntrinsicRuntime=await TeaVM.wasmGC.load('/regressions/intrinsic-metadata.wasm');pzIntrinsicRuntime.exports.main([]);});
  await expect.poll(()=>page.evaluate(()=>globalThis.pzIntrinsicProbe)).toBe('passed');
  // TeaVM's integer instructions trap on zero; do not claim that these traps
  // have been translated to catchable Java ArithmeticException instances.
  for(const wide of [false,true])await expect(page.evaluate(wide=>pzIntrinsicRuntime.exports.divideByZero(wide),wide)).rejects.toThrow(/divide by zero/i);
});

test('main thread registration and group monitor lifecycle execute',async({page})=>{
  await page.goto('/lab.html');await expect(page.locator('#status')).toHaveText('PLATAFORMA LISTA');
  await page.evaluate(async()=>{const runtime=await TeaVM.wasmGC.load('/regressions/thread-bootstrap.wasm');runtime.exports.main([]);});
  await expect.poll(()=>page.evaluate(()=>globalThis.pzThreadBootstrapProbe)).toBe('passed');
});
