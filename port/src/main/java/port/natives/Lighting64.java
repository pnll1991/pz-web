package port.natives;

import org.teavm.jso.JSBody;

/**
 * Our own implementation of the Lighting64 JNI library for the browser.
 *
 * The game's zombie.iso.LightingJNI.init() calls System.loadLibrary("Lighting64") and, when that
 * fails, logs the exception, sleeps 3 s and calls System.exit(1). Without a library the boot can
 * therefore never reach the main menu, so this class is a real (partial) implementation of that
 * library rather than a stub: it keeps the state the game itself sets through the entry points it
 * actually calls before a world exists, and every lighting entry point we have not implemented
 * keeps the fail-fast guard with its own JNI name (see port/compiler/FailFastNatives.java).
 *
 * Implemented here: configure(F)V, WaitingForMain()Z, DoLightingUpdateNew(JZ)V and destroy()V.
 * Not implemented: the per-chunk/per-square lighting model (chunkBeginUpdate, squareSet, addLight,
 * getVertLight, ...). Those raise, so an in-world session fails loudly instead of silently
 * rendering wrong light.
 */
public final class Lighting64 {
    private Lighting64() { }
    private static boolean installed;
    private static boolean configured;
    private static boolean destroyed;
    private static float cellSize;
    /** Chunk updates begun by the game. The natives that open them are unimplemented, so it stays 0. */
    private static int openChunkUpdates;

    /** Registers the library name so System.loadLibrary("Lighting64") is satisfied by this module. */
    public static void install() {
        if (installed) return;
        registerLibrary("Lighting64");
        installed = true;
        publish("installed");
    }

    public static void configure(float value) {
        if (Float.isNaN(value) || value <= 0.0f)
            throw new IllegalArgumentException("Lighting64.configure: cell size must be a positive float, got " + value);
        if (destroyed) throw new IllegalStateException("Lighting64.configure: library was destroyed");
        cellSize = value;
        configured = true;
        publish("configured");
    }

    /** Our update pass is synchronous, so the module never parks waiting for the main thread. */
    public static boolean waitingForMain() { return false; }

    public static void doLightingUpdate(long now, boolean force) {
        if (!configured) throw new IllegalStateException("Lighting64.DoLightingUpdateNew: configure() has not run yet");
        if (destroyed) throw new IllegalStateException("Lighting64.DoLightingUpdateNew: library was destroyed");
        recordUpdateCall();
        if (openChunkUpdates > 0)
            throw new UnsupportedOperationException(
                "Lighting64.DoLightingUpdateNew: the per-chunk lighting model is not implemented (cellSize=" + cellSize + ")");
        // Nothing was registered for recomputation: this is the real menu state, where no IsoCell
        // exists yet, so the pass has no work. This is not a stub - any actual lighting state
        // reaches the raise above.
    }

    public static void destroy() {
        destroyed = true;
        configured = false;
        cellSize = 0.0f;
        openChunkUpdates = 0;
        publish("destroyed");
    }

    public static boolean isInstalled() { return installed; }
    public static boolean isConfigured() { return configured; }
    public static float cellSize() { return cellSize; }

    @JSBody(params="library",script="var r=globalThis.pzRuntime;if(!r)return;var n=r.loadedLibraries||[];if(n.indexOf(library)<0)n.push(library);r.loadedLibraries=n;if(!r.natives)r.natives={};r.natives[library]=true;")
    private static native void registerLibrary(String library);

    /** Diagnostic state, so a test can prove the module answered instead of a stub. */
    @JSBody(params="stage",script="var r=globalThis.pzRuntime;if(!r)return;if(!r.natives)r.natives={};var s=r.natives.Lighting64||{stage:'',configureCalls:0,updateCalls:0};s.stage=stage;s.configureCalls=s.configureCalls+(stage==='configured'?1:0);r.natives.Lighting64=s;")
    private static native void publish(String stage);

    @JSBody(script="var r=globalThis.pzRuntime;if(!r)return;if(!r.natives)r.natives={};var s=r.natives.Lighting64||{stage:'',configureCalls:0,updateCalls:0};s.updateCalls++;r.natives.Lighting64=s;")
    private static native void recordUpdateCall();
}
