package port.shim;

import org.teavm.jso.JSBody;

/** Bridges only the Bullet operations our own wasm32 module really implements. */
public final class BulletLibrary {
    private BulletLibrary() { }
    public static void initLogging() { initLoggingNative(); }
    public static String version() { return versionNative(); }
    public static boolean worldInitialised() { return worldInitialisedNative() != 0; }
    // A missing module must raise, never look like an initialised physics library.
    @JSBody(script="var b=globalThis.pzRuntime&&globalThis.pzRuntime.bullet;if(!b)throw Error('Bullet wasm32 module is not loaded');b.initLogging();")
    private static native void initLoggingNative();
    @JSBody(script="var b=globalThis.pzRuntime&&globalThis.pzRuntime.bullet;if(!b)throw Error('Bullet wasm32 module is not loaded');var v=b.version();if(typeof v!=='string'||v.length===0)throw Error('Bullet wasm32 module reports no version');return v;")
    private static native String versionNative();
    @JSBody(script="var b=globalThis.pzRuntime&&globalThis.pzRuntime.bullet;if(!b)throw Error('Bullet wasm32 module is not loaded');var w=b.worldInitialised();if(typeof w!=='number')throw Error('Bullet wasm32 module reports no world state');return w;")
    private static native int worldInitialisedNative();
}
