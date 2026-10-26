import { open, realpath, stat, readFile } from 'node:fs/promises';
import path from 'node:path';
import { inflateRawSync, crc32 } from 'node:zlib';

const inside = (base, candidate) => { const rel = path.relative(base, candidate); return rel !== '..' && !rel.startsWith('../') && !path.isAbsolute(rel); };
const valid = name => name && !name.startsWith('/') && !name.includes('\\') && !name.includes('\0') && !name.split('/').some(p => p === '..' || p === '.');

/** Read central-directory metadata and requested entries; never extract or copy the jar. */
export async function jarResources(filename) {
  const file = await open(filename, 'r');
  try {
    const { size } = await file.stat();
    const tail = Buffer.alloc(Math.min(size, 65557)); await file.read(tail, 0, tail.length, size-tail.length);
    let end = tail.length-22;
    while (end>=0 && !(tail.readUInt32LE(end)===0x06054b50 && end+22+tail.readUInt16LE(end+20)===tail.length)) end--;
    if(end<0)throw new Error('Invalid ZIP directory');
    const count=tail.readUInt16LE(end+10), length=tail.readUInt32LE(end+12), offset=tail.readUInt32LE(end+16);
    if(count===65535 || offset===0xffffffff || length===0xffffffff)throw new Error('ZIP64 classpath is not supported');
    if(offset+length>size || length>32*1024*1024)throw new Error('Invalid ZIP directory bounds');
    const data=Buffer.alloc(length);await file.read(data,0,length,offset);const entries=new Map();let p=0;
    for(let i=0;i<count;i++){
      if(p+46>length || data.readUInt32LE(p)!==0x02014b50)throw new Error('Invalid ZIP entry');
      const flags=data.readUInt16LE(p+8), method=data.readUInt16LE(p+10), compressed=data.readUInt32LE(p+20), bytes=data.readUInt32LE(p+24);
      const n=data.readUInt16LE(p+28), extra=data.readUInt16LE(p+30), comment=data.readUInt16LE(p+32), local=data.readUInt32LE(p+42);
      const name=data.subarray(p+46,p+46+n).toString('utf8');
      if(valid(name)&&!name.endsWith('/'))entries.set(name,{ flags,method,compressed,bytes,local,crc:data.readUInt32LE(p+16) });
      p+=46+n+extra+comment;if(p>length)throw new Error('Truncated ZIP entry');
    }
    return { entries, async read(name){
      const e=entries.get(name);if(!e)return null;if(e.flags&1 || ![0,8].includes(e.method) || e.bytes>128*1024*1024)throw new Error('Unsupported classpath ZIP entry');
      const source=await open(filename,'r');try{
        const header=Buffer.alloc(30);await source.read(header,0,30,e.local);if(header.readUInt32LE(0)!==0x04034b50)throw new Error('Invalid ZIP local header');
        const start=e.local+30+header.readUInt16LE(26)+header.readUInt16LE(28);const packed=Buffer.alloc(e.compressed);
        const result=await source.read(packed,0,packed.length,start);if(result.bytesRead!==packed.length)throw new Error('Truncated classpath resource');
        const bytes=e.method===0?packed:inflateRawSync(packed,{maxOutputLength:Math.max(1,e.bytes)});
        if(bytes.length!==e.bytes)throw new Error('Invalid resource size');
        if(crc32(bytes)!==e.crc)throw new Error('Invalid classpath resource CRC');
        return bytes;
      }finally{await source.close();}
    }};
  }finally{await file.close();}
}

export async function classpathResources(roots=[]) {
  const sources=[];
  for(const filename of roots){const resolved=await realpath(filename);const info=await stat(resolved);sources.push(info.isDirectory()?{directory:resolved}:await jarResources(resolved));}
  async function local(source,name){try{const file=await realpath(path.join(source.directory,name));return inside(source.directory,file)&&(await stat(file)).isFile()?file:null;}catch(e){if(['ENOENT','ENOTDIR'].includes(e.code))return null;throw e;}}
  return {
    async lookup(name){if(!valid(name))return [];const found=[];for(let i=0;i<sources.length;i++){const s=sources[i];if(s.directory?await local(s,name):s.entries.has(name))found.push(`/classpath/resource/${i}/${name.split('/').map(encodeURIComponent).join('/')}`);}return found;},
    async read(index,name){if(!valid(name)||!Number.isInteger(index)||index<0||index>=sources.length)return null;const s=sources[index];if(!s.directory)return s.read(name);const file=await local(s,name);return file?readFile(file):null;}
  };
}
