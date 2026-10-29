package port.natives;

/**
 * Our browser implementation of the PZPathFind64 JNI library
 * (zombie.pathfind.nativeCode.PathfindNative).
 *
 * GameWindow.init() calls PathfindNative.init(), whose only effect is System.loadLibrary
 * ("PZPathFind64"); GameWindow.exit() calls the static freeMemoryAtExit()V. Both are library
 * lifecycle, so this module provides them; the pathfinding model (initWorld, updateWorld chunks,
 * updateSquare, addVehicle, findPath, destroyWorld) is unimplemented and keeps its fail-fast guard
 * with the JNI name, so an in-world session raises rather than returning a wrong path.
 */
public final class PathFind64 {
    private PathFind64() { }
    private static boolean memoryReleased;

    public static void install() {
        NativeRegistry.register("PZPathFind64");
        NativeRegistry.publish("PZPathFind64", "installed", null);
    }

    /** We never allocate native memory, so releasing it has nothing to do; the call is recorded. */
    public static void freeMemoryAtExit() {
        memoryReleased = true;
        NativeRegistry.publish("PZPathFind64", "memoryReleased", "freeMemoryAtExit");
    }

    public static boolean isMemoryReleased() { return memoryReleased; }
}
