package port;

import org.teavm.jso.JSBody;

/** Browser entrypoint; the compiler links launch to the inventoried launcher. */
public final class GameBoot {
    public static void main(String[] args) {
        port.shim.BrowserFileSystem.install();
        // The game reads System.getProperty("user.language"); the browser supplies it here.
        port.shim.BrowserPlatform.installLocale();
        // Lighting64 is a native library the browser cannot load; our own module answers the
        // entry points the boot reaches before a world exists (see port/natives/Lighting64.java).
        port.natives.Lighting64.install();
        // Same pattern for the libraries GameWindow.init() loads right after LightingJNI:
        // PZPopMan64, PZClipper64 and PZPathFind64 (see port/natives/*.java).
        port.natives.PopMan64.install();
        port.natives.Clipper2.install();
        port.natives.PathFind64.install();
        state("starting", "", "");
        try {
            launch(args);
            state("main-returned", "", "");
        } catch (Throwable error) {
            StringBuilder trace=new StringBuilder();
            for(StackTraceElement frame:error.getStackTrace())trace.append(frame).append('\n');
            // The game may replace System.err; keep a separate diagnostic channel.
            state("failed", error.toString(), trace.toString());
            error.printStackTrace();
        }
    }

    private static void launch(String[] args) {
        throw new IllegalStateException("Game entrypoint was not linked by the compiler");
    }

    @JSBody(params={"phase", "error", "trace"}, script="globalThis.pzGame={phase:phase,error:error,trace:trace,menuRendered:false};")
    private static native void state(String phase, String error, String trace);
}
