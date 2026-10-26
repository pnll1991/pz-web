package port;

import org.teavm.jso.JSBody;

/** Browser entrypoint; the compiler links launch to the inventoried launcher. */
public final class GameBoot {
    public static void main(String[] args) {
        port.shim.BrowserFileSystem.install();
        state("starting", "");
        try {
            launch(args);
            state("main-returned", "");
        } catch (Throwable error) {
            state("failed", error.toString());
            error.printStackTrace();
        }
    }

    private static void launch(String[] args) {
        throw new IllegalStateException("Game entrypoint was not linked by the compiler");
    }

    @JSBody(params={"phase", "error"}, script="globalThis.pzGame={phase:phase,error:error,menuRendered:false};")
    private static native void state(String phase, String error);
}
