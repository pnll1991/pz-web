package port.natives;

import java.util.HashMap;
import java.util.Map;

/**
 * Our browser implementation of the PZPopMan64 JNI library (zombie.popman.ZombiePopulationManager).
 *
 * Why this exists: ZombiePopulationManager.init() does System.loadLibrary("PZPopMan64") from
 * GameWindow.init(); without a library the UnsatisfiedLinkError propagates out of the main thread
 * ("GameThread exited..") and the render loop ends with System.exit(0) before any menu frame
 * (STATUS.md round 42). The game's init() itself only loads the library and registers a
 * Trigger_Zombie.xml watcher, so the boot needs the library to exist, not the simulation.
 *
 * Real partial implementation, no fake success:
 *  - implemented: the module-level state the game sets before/independently of a world
 *    (init, config floats/ints, spawn origins, outfit names, save/stop of that state, and the
 *    three "is there pending work" queries, which are honestly false because our pass is
 *    synchronous and no population data was ever registered);
 *  - unimplemented: the population model itself (addZombie, registerZombie, updateMain,
 *    updateThread, loadChunk, loadedAreas, updateRealZombies, spawnHorde, worldSound, aggro
 *    target, radar reads). Those keep the fail-fast guard with their JNI name from the
 *    inventory, so an in-world session raises instead of pretending zombies exist.
 */
public final class PopMan64 {
    private PopMan64() { }
    private static boolean initialized;
    private static boolean stopped;
    private static boolean debugZombies;
    private static boolean singlePlayer;
    private static int gridWidth;
    private static int gridHeight;
    private static int chunkWidth;
    private static int chunkHeight;
    private static final Map<String, Float> floats = new HashMap<>();
    private static final Map<String, Integer> ints = new HashMap<>();
    private static int[] spawnOrigins;
    private static String[] outfitNames;

    /** Registers the library name so System.loadLibrary("PZPopMan64") is satisfied by this module. */
    public static void install() {
        NativeRegistry.register("PZPopMan64");
        NativeRegistry.publish("PZPopMan64", "installed", null);
    }

    public static void init(boolean debugLogging, boolean singlePlayerMode, int width, int height, int chunkW, int chunkH) {
        if (stopped) throw new IllegalStateException("PZPopMan64.n_init: the module was stopped");
        if (width <= 0 || height <= 0 || chunkW <= 0 || chunkH <= 0)
            throw new IllegalArgumentException("PZPopMan64.n_init: grid and chunk sizes must be positive, got "
                + width + "x" + height + ", chunk " + chunkW + "x" + chunkH);
        debugZombies = debugLogging;
        singlePlayer = singlePlayerMode;
        gridWidth = width;
        gridHeight = height;
        chunkWidth = chunkW;
        chunkHeight = chunkH;
        initialized = true;
        NativeRegistry.publish("PZPopMan64", "initialized", "init");
    }

    public static void configFloat(String name, float value) {
        require(name);
        if (Float.isNaN(value)) throw new IllegalArgumentException("PZPopMan64.n_configFloat: " + name + " is NaN");
        floats.put(name, value);
        NativeRegistry.publish("PZPopMan64", "configured", "configFloat");
    }

    public static void configInt(String name, int value) {
        require(name);
        ints.put(name, value);
        NativeRegistry.publish("PZPopMan64", "configured", "configInt");
    }

    /** Batch configuration (the game uses n_configFloat/n_configInt per option, not this one). */
    public static void config(float minPerChunk, float maxPerChunk, float spawnRadius, int gridCells,
                              float hordeSpacing, float noiseReduction, float minDistance, float maxDistance, int seed) {
        if (Float.isNaN(minPerChunk) || Float.isNaN(maxPerChunk) || minPerChunk < 0 || maxPerChunk < minPerChunk)
            throw new IllegalArgumentException("PZPopMan64.n_config: zombie per-chunk range is invalid: "
                + minPerChunk + ".." + maxPerChunk);
        if (gridCells <= 0) throw new IllegalArgumentException("PZPopMan64.n_config: gridCells must be positive, got " + gridCells);
        if (Float.isNaN(spawnRadius) || Float.isNaN(hordeSpacing) || Float.isNaN(noiseReduction)
            || Float.isNaN(minDistance) || Float.isNaN(maxDistance))
            throw new IllegalArgumentException("PZPopMan64.n_config: a configuration float is NaN");
        floats.put("zombiesMinPerChunk", minPerChunk);
        floats.put("zombiesMaxPerChunk", maxPerChunk);
        floats.put("spawnRadius", spawnRadius);
        ints.put("gridCells", gridCells);
        floats.put("hordeSpacing", hordeSpacing);
        floats.put("noiseReduction", noiseReduction);
        floats.put("minDistance", minDistance);
        floats.put("maxDistance", maxDistance);
        ints.put("seed", seed);
        NativeRegistry.publish("PZPopMan64", "configured", "config");
    }

    /** Spawn origins as packed grid cells, as zombie.popman.ZombiePopulationManager#init sends them. */
    public static void setSpawnOrigins(int[] origins) {
        if (origins == null) throw new IllegalArgumentException("PZPopMan64.n_setSpawnOrigins: origins is null");
        if (origins.length % 2 != 0)
            throw new IllegalArgumentException("PZPopMan64.n_setSpawnOrigins: expected x/y pairs, got " + origins.length + " ints");
        for (int origin : origins) if (origin < 0) throw new IllegalArgumentException("PZPopMan64.n_setSpawnOrigins: negative cell " + origin);
        spawnOrigins = origins.clone();
        NativeRegistry.publish("PZPopMan64", "spawnOrigins", "setSpawnOrigins");
    }

    public static void setOutfitNames(String[] names) {
        if (names == null) throw new IllegalArgumentException("PZPopMan64.n_setOutfitNames: names is null");
        for (String name : names) if (name == null) throw new IllegalArgumentException("PZPopMan64.n_setOutfitNames: null outfit name");
        outfitNames = names.clone();
        NativeRegistry.publish("PZPopMan64", "outfitNames", "setOutfitNames");
    }

    /**
     * The pending-work queries. There is never an asynchronous pass in this module: the population
     * model is unimplemented, so no data can be waiting for the main or sim thread. Returning true
     * here would park the game loop on work that does not exist, so false is the honest answer.
     */
    public static boolean hasDataForThread() { return false; }
    public static boolean readyToPause() { return true; }
    public static boolean shouldWait() { return false; }

    /** save/saveCell only flush state we hold; there is no serialized population to write out. */
    public static void save() {
        requireInitialized();
        NativeRegistry.publish("PZPopMan64", "saved", "save");
    }

    public static void saveCell(int x, int y) {
        requireInitialized();
        if (x < 0 || y < 0) throw new IllegalArgumentException("PZPopMan64.n_saveCell: negative cell " + x + "," + y);
        NativeRegistry.publish("PZPopMan64", "savedCell", "saveCell");
    }

    public static void stop() {
        stopped = true;
        initialized = false;
        floats.clear();
        ints.clear();
        spawnOrigins = null;
        outfitNames = null;
        NativeRegistry.publish("PZPopMan64", "stopped", "stop");
    }

    public static boolean isInitialized() { return initialized; }
    public static boolean isStopped() { return stopped; }
    public static int gridWidth() { return gridWidth; }
    public static int gridHeight() { return gridHeight; }
    public static int chunkWidth() { return chunkWidth; }
    public static int chunkHeight() { return chunkHeight; }
    public static int spawnOriginCount() { return spawnOrigins == null ? 0 : spawnOrigins.length / 2; }
    public static int outfitNameCount() { return outfitNames == null ? 0 : outfitNames.length; }
    public static Float floatConfig(String name) { return floats.get(name); }
    public static Integer intConfig(String name) { return ints.get(name); }

    private static void require(String name) {
        if (name == null) throw new IllegalArgumentException("PZPopMan64: a configuration name is null");
    }

    private static void requireInitialized() {
        if (!initialized) throw new IllegalStateException("PZPopMan64: n_init has not run yet");
    }
}
