package port.natives;

/**
 * Our browser implementation of the PZClipper64 JNI library (zombie.vehicles.Clipper).
 *
 * GameWindow.init() calls Clipper.init(), which does System.loadLibrary("PZClipper64") and then the
 * static n_init()V. Without the library the main thread dies with an UnsatisfiedLinkError, exactly
 * like Lighting64/PZPopMan64 did (STATUS.md round 42), so this module provides the library.
 *
 * Round 80: n_init()V only prepares the library (we hold no native memory). The geometry natives of
 * Clipper and ClipperOffset are instance methods; port.compiler.ClipperNativeCompatibility forwards
 * them to port.natives.ClipperEngine / ClipperOffsetEngine (Clipper2-java + earcut4j), one engine per
 * game object.
 */
public final class Clipper2 {
    private Clipper2() { }
    private static boolean initialized;

    public static void install() {
        NativeRegistry.register("PZClipper64");
        NativeRegistry.publish("PZClipper64", "installed", null);
    }

    public static void init() {
        initialized = true;
        NativeRegistry.publish("PZClipper64", "initialized", "init");
    }

    public static boolean isInitialized() { return initialized; }
}
