package port.natives;

import org.teavm.jso.JSBody;

/**
 * Shared registration channel for our browser-side implementations of the game's JNI libraries.
 *
 * JdkSupport.loadLibrary only accepts a name that a module registered here, so a library can never
 * appear "loaded" without an implementation behind it. The published state is what a Playwright
 * test reads back to prove the module answered instead of a stub.
 */
public final class NativeRegistry {
    private NativeRegistry() { }

    /** Marks the library as provided by one of our modules (System.loadLibrary checks this). */
    @JSBody(params="library",script="var r=globalThis.pzRuntime;if(!r)return;var n=r.loadedLibraries||[];if(n.indexOf(library)<0)n.push(library);r.loadedLibraries=n;if(!r.natives)r.natives={};var s=r.natives[library];if(!s||typeof s!=='object')r.natives[library]={stage:'registered',calls:{}};")
    public static native void register(String library);

    /** Records a stage plus a call counter per library, so a test can distinguish real answers. */
    @JSBody(params={"library","stage","counter"},script="var r=globalThis.pzRuntime;if(!r)return;if(!r.natives)r.natives={};var s=r.natives[library];if(!s||typeof s!=='object'){s={stage:'',calls:{}};r.natives[library]=s;}s.stage=stage;"
        + "if(counter)s.calls[counter]=(s.calls[counter]||0)+1;r.natives[library]=s;")
    public static native void publish(String library, String stage, String counter);
}
