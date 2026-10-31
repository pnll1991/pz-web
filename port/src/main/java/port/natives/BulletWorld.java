package port.natives;

/**
 * Round 79: the world side of the game's Bullet JNI API (zombie.core.physics.Bullet), as our own
 * browser module. WorldSimulation.create() calls initWorld during IsoWorld.init, then every frame
 * activates/scrolls the chunk window, uploads chunk collision (ToBullet) and steps the simulation.
 *
 * Documented simplification: a physics world WITHOUT rigid bodies. Chunk collision data is accepted
 * and counted but not turned into Bullet shapes; there are no vehicles, physics objects, ragdolls or
 * ballistics. Every native that would create or query such a body (addVehicle, addPhysicsObject,
 * addRagdoll, getBallisticsTargets, ...) keeps the FailFastNatives guard with its JNI name, so using
 * one raises instead of pretending. The count queries answer the true 0 of this world.
 */
public final class BulletWorld {
    private BulletWorld() { }
    private static boolean initialised;
    private static int chunkUploads, steps;
    private static float simulatedSeconds;

    public static void initWorld(int a, int b, int c, int d, int e, int f, boolean server) {
        initialised = true; steps = 0; simulatedSeconds = 0;
        NativeRegistry.publish("PZBullet", "worldInitialised", "initWorld");
    }
    public static boolean isWorldInit() { return initialised; }
    public static void destroyWorld() { initialised = false; NativeRegistry.publish("PZBullet", "worldDestroyed", "destroyWorld"); }
    public static void activateChunkMap(int player, int wx, int wy, int width) { requireWorld("activateChunkMap"); }
    public static void deactivateChunkMap(int player) { }
    public static void scrollChunkMap(int player, int direction) { requireWorld("scrollChunkMap"); }
    public static void setChunkMinMaxLevel(int wx, int wy, int min, int max) { }
    /** Chunk collision upload; recorded only (no shapes in this world). */
    public static void ToBullet(java.nio.ByteBuffer data) { chunkUploads++; }
    public static void stepSimulation(float dt, int maxSubSteps, float fixedStep) {
        requireWorld("stepSimulation");
        steps++; simulatedSeconds += Math.max(0, dt);
    }
    public static int getVehicleCount() { return 0; }
    /** No vehicles exist, so no vehicle record is written. */
    public static int getVehiclePhysics(int index, float[] out) { return 0; }
    /** No physics objects exist, so no object record is written. */
    public static int getObjectPhysics(float[] out) { return 0; }
    /** Definition uploads made while scripts load (RagdollScript.toBullet in ScriptManager.PostLoadScripts,
     * vehicle/physics meshes). A missing native there flags a script load error and IsoWorld.init
     * refuses the world. The definitions are stored (latest wins per key) and reported as accepted;
     * ragdolls and meshes are not simulated by this body-less world. */
    private static final java.util.Map<String, float[]> definitions = new java.util.HashMap<>();
    private static boolean define(String kind, float[] values) {
        if (values == null) throw new IllegalArgumentException("PZBullet." + kind + ": values is null");
        definitions.put(kind, values.clone());
        return true;
    }
    public static boolean defineRagdollConstraints(float[] v, boolean isDefault) { return define("ragdollConstraints:" + isDefault, v); }
    public static boolean defineRagdollAnchors(float[] v, boolean isDefault) { return define("ragdollAnchors:" + isDefault, v); }
    public static boolean defineRagdollBodyPartInfo(float[] v, boolean isDefault) { return define("ragdollBodyPartInfo:" + isDefault, v); }
    public static boolean defineRagdollBodyDynamics(float[] v, boolean isDefault) { return define("ragdollBodyDynamics:" + isDefault, v); }
    public static void definePhysicsMesh(int id, boolean flag, float[] v) { define("physicsMesh:" + id, v); }
    public static void defineVehiclePhysicsMesh(String name, int index, float[] v) { define("vehicleMesh:" + name + ":" + index, v); }
    public static void clearPhysicsMeshes() { definitions.keySet().removeIf(k -> k.startsWith("physicsMesh:")); }
    public static int definitionCount() { return definitions.size(); }
    public static int chunkUploads() { return chunkUploads; }
    public static int steps() { return steps; }

    private static void requireWorld(String what) {
        if (!initialised) throw new IllegalStateException("PZBullet." + what + ": initWorld has not run");
    }
}
