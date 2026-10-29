package port.natives;

/**
 * Our browser implementation of the PZClipper64 JNI library (zombie.vehicles.Clipper).
 *
 * GameWindow.init() calls Clipper.init(), which does System.loadLibrary("PZClipper64") and then the
 * static n_init()V. Without the library the main thread dies with an UnsatisfiedLinkError, exactly
 * like Lighting64/PZPopMan64 did (STATUS.md round 42), so this module provides the library.
 *
 * Scope, kept honest:
 *  - implemented: n_init()V, which only prepares the library. We hold no native memory, so this is
 *    a state transition we can really honour and report;
 *  - unimplemented: the Clipper2 geometry operations themselves (newInstance, addPath, addPolygon,
 *    clipAABB, generatePolygons, triangulate, ClipperOffset.*). Those keep the fail-fast guard with
 *    their JNI name, so vehicle/clipper geometry raises instead of producing wrong polygons.
 *    Implementing Clipper2 is a real task (Boost clipper per docs/ROADMAP-source.txt), not a shim.
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
