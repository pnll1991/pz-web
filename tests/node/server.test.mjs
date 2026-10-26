import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, symlink, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { once } from 'node:events';
import { createDevServer, parseRange } from '../../dev-server/server.mjs';

let temporary, server, origin;
before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-server-test-'));
  await mkdir(path.join(temporary,'web'));
  await mkdir(path.join(temporary,'game/media'),{recursive:true});
  await writeFile(path.join(temporary,'web/index.html'),'synthetic platform');
  await writeFile(path.join(temporary,'web/boot.wasm'),Buffer.from([0,97,115,109]));
  await writeFile(path.join(temporary,'game/ProjectZomboid64.json'),'{}');
  await writeFile(path.join(temporary,'game/media/text.txt'),'0123456789');
  await writeFile(path.join(temporary,'outside.txt'),'must not serve');
  await symlink(path.join(temporary,'outside.txt'),path.join(temporary,'game/media/link.txt'));
  server = await createDevServer({webRoot:path.join(temporary,'web'),pzHome:path.join(temporary,'game')});
  server.listen(0,'127.0.0.1'); await once(server,'listening');
  origin = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { await new Promise(resolve=>server.close(resolve)); await rm(temporary,{recursive:true}); });

test('loopback, isolation headers and wasm MIME', async () => {
  assert.equal(server.address().address,'127.0.0.1');
  const response = await fetch(origin+'/boot.wasm');
  assert.equal(response.status,200);
  assert.equal(response.headers.get('content-type'),'application/wasm');
  assert.equal(response.headers.get('cross-origin-opener-policy'),'same-origin');
  assert.equal(response.headers.get('cross-origin-embedder-policy'),'require-corp');
  assert.equal((await response.arrayBuffer()).byteLength,4);
});
test('real byte ranges, suffixes, HEAD and invalid ranges', async () => {
  for (const [range,expected] of [['bytes=2-4','234'],['bytes=-3','789'],['bytes=7-','789']]) {
    const response = await fetch(origin+'/media/text.txt',{headers:{Range:range}});
    assert.equal(response.status,206); assert.equal(await response.text(),expected);
  }
  const invalid = await fetch(origin+'/media/text.txt',{headers:{Range:'bytes=99-'}});
  assert.equal(invalid.status,416); assert.equal(invalid.headers.get('content-range'),'bytes */10');
  const head = await fetch(origin+'/media/text.txt',{method:'HEAD',headers:{Range:'bytes=0-2'}});
  assert.equal(head.status,206); assert.equal(head.headers.get('content-length'),'3'); assert.equal(await head.text(),'');
  assert.equal(parseRange('bytes=0-1,4-5',10),null);
});
test('ETag conditional reads and If-Range fallback', async () => {
  const first = await fetch(origin+'/media/text.txt'); const etag = first.headers.get('etag');
  await first.text();
  const cached = await fetch(origin+'/media/text.txt',{headers:{'If-None-Match':etag}});
  assert.equal(cached.status,304); assert.equal(await cached.text(),'');
  const fallback = await fetch(origin+'/media/text.txt',{headers:{Range:'bytes=0-2','If-Range':etag}});
  assert.equal(fallback.status,200); assert.equal(await fallback.text(),'0123456789');
});
test('game mount read only and symlinks cannot leave mount', async () => {
  assert.equal((await fetch(origin+'/media/text.txt',{method:'PUT',body:'overwrite'})).status,405);
  assert.equal((await fetch(origin+'/media/link.txt')).status,403);
  assert.equal((await fetch(origin+'/ProjectZomboid64.json')).status,404);
  assert.equal(await (await fetch(origin+'/media/text.txt')).text(),'0123456789');
});
test('cross-origin access and foreign Host are rejected', async () => {
  assert.equal((await fetch(origin+'/media/text.txt',{headers:{Origin:'https://example.invalid'}})).status,403);
  const status = await new Promise((resolve,reject) => {
    const request = http.get(origin+'/media/text.txt',{headers:{Host:'example.invalid'}},response=>{response.resume();resolve(response.statusCode);});
    request.on('error',reject);
  });
  assert.equal(status,403);
});
