import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp,mkdir,writeFile,rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { classpathResources } from '../../dev-server/classpath.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';

test('classpath preserves ordered duplicates and reads actual compressed ZIP bytes',async()=>{
  const temporary=await mkdtemp(path.join(tmpdir(),'pz-classpath-'));
  try{
    const folder=path.join(temporary,'directory');await mkdir(path.join(folder,'own'),{recursive:true});await writeFile(path.join(folder,'own/data.txt'),'directory ñ');
    const jar=path.join(temporary,'fixture.jar');const result=spawnSync('python3',['-c',"import zipfile,sys\nwith zipfile.ZipFile(sys.argv[1],'w',compression=zipfile.ZIP_DEFLATED) as z:z.writestr('own/data.txt','jar 😀');z.writestr('empty.txt','')",jar],{encoding:'utf8'});assert.equal(result.status,0,result.stderr);
    const resources=await classpathResources([folder,jar]);assert.deepEqual(await resources.lookup('own/data.txt'),['/classpath/resource/0/own/data.txt','/classpath/resource/1/own/data.txt']);
    assert.equal((await resources.read(0,'own/data.txt')).toString(),'directory ñ');assert.equal((await resources.read(1,'own/data.txt')).toString(),'jar 😀');assert.equal((await resources.read(1,'empty.txt')).length,0);assert.deepEqual(await resources.lookup('absent.txt'),[]);
  }finally{await rm(temporary,{recursive:true});}
});

test('VFS watch notifications distinguish create, modify, rename and unsubscribe',()=>{
  const vfs=new VirtualFileSystem();const events=[];const detach=vfs.onChange((name,kind)=>events.push([name,kind]));
  vfs.mkdir('/Zomboid/watch');vfs.writeFile('/Zomboid/watch/a',new Uint8Array([1]));vfs.writeFile('/Zomboid/watch/a',new Uint8Array([2]));vfs.rename('/Zomboid/watch/a','/Zomboid/watch/b');vfs.remove('/Zomboid/watch/b');detach();vfs.remove('/Zomboid/watch');
  assert.deepEqual(events,[['/Zomboid/watch','create'],['/Zomboid/watch/a','create'],['/Zomboid/watch/a','modify'],['/Zomboid/watch/a','delete'],['/Zomboid/watch/b','create'],['/Zomboid/watch/b','delete']]);
});
