/** JDBC bridge over official SQLite Wasm; durable snapshots use the existing VFS. */
export async function sqliteBridge(vfs) {
  if (!globalThis.sqlite3InitModule) await new Promise((resolve,reject)=>{
    const script=document.createElement('script');script.src='/vendor/sqlite/sqlite3.js';script.onload=resolve;script.onerror=()=>reject(new Error('SQLite loader unavailable'));document.head.append(script);
  });
  const sqlite=await globalThis.sqlite3InitModule({locateFile:name=>'/vendor/sqlite/'+name});
  const active=new Map();
  const attempt=work=>{try{return {value:work(),error:null};}catch(error){return {value:null,error:String(error)};}};
  return {
    version:sqlite.version.libVersion,
    open(filename){return attempt(()=>{
      const persistent=filename!==':memory:';
      if(persistent && (!filename.startsWith('/Zomboid/') || filename.split('/').includes('..')))throw new Error('SQLite path must be in /Zomboid');
      if(persistent&&active.has(filename))throw new Error('Concurrent connections to the same VFS database are not supported yet');
      const db=new sqlite.oo1.DB(':memory:');
      try {
        if(persistent&&vfs.exists(filename)){
          const bytes=vfs.readFile(filename), pointer=sqlite.wasm.allocFromTypedArray(bytes);
          const rc=sqlite.capi.sqlite3_deserialize(db.pointer,'main',pointer,BigInt(bytes.length),BigInt(bytes.length),sqlite.capi.SQLITE_DESERIALIZE_FREEONCLOSE|sqlite.capi.SQLITE_DESERIALIZE_RESIZEABLE);
          if(rc){sqlite.wasm.dealloc(pointer);throw new Error('SQLite deserialize error '+rc);}
        }
        const connection={db,filename,persistent,closed:false};if(persistent)active.set(filename,connection);return connection;
      }catch(error){db.close();throw error;}
    });},
    run(connection,sql,bindings){return attempt(()=>{
      if(connection.closed)throw new Error('Connection is closed');const db=connection.db;
      const before=db.changes(true);const statement=db.prepare(sql);
      try {
        if(bindings.length!==statement.parameterCount || bindings.some(x=>x===undefined))throw new Error('All SQL parameters must be bound');
        if(bindings.length)statement.bind(bindings);
        const columns=statement.columnCount?statement.getColumnNames():[],rows=[];while(statement.step())rows.push(statement.get([]));
        return {columns,rows,updates:db.changes(true)===before?0:db.changes(),autocommit:!!sqlite.capi.sqlite3_get_autocommit(db.pointer),rowid:String(db.selectValue('SELECT last_insert_rowid()'))};
      }finally{statement.finalize();}
    });},
    parameterCount(connection,sql){return attempt(()=>{const statement=connection.db.prepare(sql);try{return statement.parameterCount;}finally{statement.finalize();}});},
    export(connection){return attempt(()=>{
      if(connection.closed)throw new Error('Connection is closed');
      if(!sqlite.capi.sqlite3_get_autocommit(connection.db.pointer))throw new Error('Cannot persist an uncommitted SQLite transaction');
      if(connection.persistent)vfs.writeFile(connection.filename,sqlite.capi.sqlite3_js_db_export(connection.db));return connection.persistent;
    });},
    close(connection){return attempt(()=>{if(!connection.closed){connection.db.close();connection.closed=true;if(connection.persistent)active.delete(connection.filename);}return null;});},
  };
}
