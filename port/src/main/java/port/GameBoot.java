package port;

import org.teavm.jso.JSBody;

/** Browser entrypoint; the compiler links launch to the inventoried launcher. */
public final class GameBoot {
    public static void main(String[] args) {
        port.shim.BrowserFileSystem.install();
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
