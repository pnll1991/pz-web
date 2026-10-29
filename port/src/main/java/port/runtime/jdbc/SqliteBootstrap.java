package port.runtime.jdbc;

import org.teavm.jso.JSBody;

/**
 * Browser replacement for zombie.util.PZSQLUtils.init().
 *
 * The game's init() extracts a native sqlitejdbc library out of the jar and then calls
 * Class.forName("org.sqlite.JDBC"); when that reflection lookup fails it logs and calls
 * System.exit(1), which kills the main thread and the render loop with it (STATUS.md round 42,
 * step 2). Neither exists in WebAssembly GC: our java.sql.DriverManager answers "jdbc:sqlite:"
 * urls over the SQLite Wasm module (port/runtime/sqlite.mjs), so a real init() only has to prove
 * that module is up. If it is not up, we raise instead of pretending the driver was registered.
 */
public final class SqliteBootstrap {
    private SqliteBootstrap() { }

    public static void init() {
        String version = moduleVersion();
        if (version == null || version.isEmpty())
            throw new IllegalStateException("PZSQLUtils.init: the SQLite Wasm module reports no version");
        record(version);
    }

    @JSBody(script="if(!globalThis.pzRuntime||!globalThis.pzRuntime.sqlite)return null;return globalThis.pzRuntime.sqlite.version||null;")
    private static native String moduleVersion();

    @JSBody(params="version",script="var r=globalThis.pzRuntime;if(!r)return;if(!r.sqlitedb)r.sqlitedb={};r.sqlitedb.version=version;r.sqlitedb.initCalls=(r.sqlitedb.initCalls||0)+1;")
    private static native void record(String version);
}
