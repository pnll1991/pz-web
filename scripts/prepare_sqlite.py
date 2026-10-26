#!/usr/bin/env python3
"""Fetch a hash-verified official SQLite Wasm into the private toolchain cache."""
import hashlib,io,json,urllib.request,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
lock=json.loads((root/'scripts/sqlite-lock.json').read_text())
cache=root/'.toolchain/sqlite-wasm';cache.mkdir(parents=True,exist_ok=True)
archive=cache/'release.zip'
if not archive.exists():
    data=urllib.request.urlopen(lock['url'],timeout=60).read()
    if hashlib.sha3_256(data).hexdigest()!=lock['sha3_256']:raise RuntimeError('SQLite download hash mismatch')
    archive.write_bytes(data)
data=archive.read_bytes()
if hashlib.sha3_256(data).hexdigest()!=lock['sha3_256']:raise RuntimeError('SQLite cached archive hash mismatch')
output=root/'port/target/web/vendor/sqlite';output.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(io.BytesIO(data)) as bundle:
    for filename in ['sqlite3.js','sqlite3.wasm']:
        matches=[n for n in bundle.namelist() if n.endswith('/'+filename)]
        if len(matches)!=1:raise RuntimeError('Unexpected SQLite bundle layout')
        (output/filename).write_bytes(bundle.read(matches[0]))
print('Prepared official SQLite Wasm '+lock['version'])
