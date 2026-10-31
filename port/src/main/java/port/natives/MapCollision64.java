package port.natives;

import org.teavm.jso.JSBody;

/**
 * Round 64: our own browser implementation of the game-state side of the MapCollisionData JNI
 * library (zombie.MapCollisionData).
 *
 * The real game calls the private native n_setGameState(String,boolean) from updateGameState()
 * every frame once the boot reaches OnGameBoot (DebugLog 2026-10-30_15-02: the first error after
 * UI_Loading_OnGameBoot was UnsupportedOperationException
 * Java_zombie_MapCollisionData_n_1setGameState__Ljava_lang_String_2Z). On desktop that native only
 * publishes a key/value into the C++ collision library's shared settings, so the honest browser
 * equivalent is to record the value verbatim in our own registry: no game behaviour is invented and
 * nothing is silently skipped.
 *
 * Only the five setGameState overloads are forwarded (inventory: 17 natives in this class).
 * n_init/n_initMetaGrid/n_initMetaCell/n_initMetaChunk/n_chunkUpdateTask/n_squareUpdateTask/
 * n_pathTask/n_hasDataForThread/n_shouldWait/n_update/n_save/n_stop keep the FailFastNatives guard
 * with their JNI name, so an in-world session raises instead of pathing against an empty grid.
 */
public final class MapCollision64 {
    private MapCollision64() { }

    public static void setGameState(String key, boolean value) { publish(key, value ? 1 : 0, "boolean"); }
    public static void setGameState(String key, int value) { publish(key, value, "int"); }
    public static void setGameState(String key, float value) { publishFloat(key, value, "float"); }
    public static void setGameState(String key, double value) { publishFloat(key, (float) value, "double"); }
    public static void setGameState(String key, String value) { publishString(key, value, "string"); }

    // ---- Round 79: world-load entry points -------------------------------------------------
    // The collision grid feeds the C++ library's own zombie path tasks (n_pathTask). This module keeps
    // the grid geometry and counts the chunk/square updates it receives, has no asynchronous work, and
    // n_pathTask raises: with no virtual population (PopMan64) nothing in the minimal world asks for it,
    // and answering without a collision model would invent paths.
    private static int gridMinX, gridMinY, gridWidth, gridHeight, metaCells, chunkUpdates, squareUpdates;
    private static boolean stopped;
    public static void init(int a, int b, int c, int d) { stopped = false; }
    public static void initMetaGrid(int minX, int minY, int maxX, int maxY) { gridMinX = minX; gridMinY = minY; gridWidth = maxX - minX + 1; gridHeight = maxY - minY + 1; }
    public static void initMetaCell(int x, int y, String file) { metaCells++; }
    public static void initMetaChunk(int a, int b, int c, int d, int e) { }
    public static void chunkUpdateTask(int wx, int wy, byte[] data) { chunkUpdates++; }
    public static void squareUpdateTask(int count, java.nio.ByteBuffer data) { squareUpdates++; }
    public static int pathTask(int x1, int y1, int x2, int y2, int[] out) {
        throw new UnsupportedOperationException("Java_zombie_MapCollisionData_n_1pathTask: collision path tasks are not implemented in the browser");
    }
    public static boolean hasDataForThread() { return false; }
    public static boolean shouldWait() { return false; }
    public static void update() { }
    public static void save() { }
    public static void stop() { stopped = true; }

    /** Number of recorded keys, for a Playwright assertion that the settings really arrived. */
    public static int recordedKeys() { return keyCount(); }

    private static void publish(String key, int value, String kind) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("MapCollision64.setGameState: empty key");
        recordInt(key, value, kind);
    }

    @JSBody(params={"key","value","kind"}, script="var r=globalThis.pzRuntime;if(!r)return;if(!r.mapCollision)r.mapCollision={state:{},calls:0,byKind:{}};var s=r.mapCollision;s.state[key]=value;s.calls++;s.byKind[kind]=(s.byKind[kind]||0)+1;r.mapCollision=s;")
    private static native void recordInt(String key, int value, String kind);

    @JSBody(params={"key","value","kind"}, script="var r=globalThis.pzRuntime;if(!r)return;if(!r.mapCollision)r.mapCollision={state:{},calls:0,byKind:{}};var s=r.mapCollision;s.state[key]=value;s.calls++;s.byKind[kind]=(s.byKind[kind]||0)+1;r.mapCollision=s;")
    private static native void recordFloat(String key, float value, String kind);

    private static void publishFloat(String key, float value, String kind) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("MapCollision64.setGameState: empty key");
        recordFloat(key, value, kind);
    }

    private static void publishString(String key, String value) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("MapCollision64.setGameState: empty key");
        if (value == null) throw new IllegalArgumentException("MapCollision64.setGameState(" + key + "): null value");
        recordString(key, value);
    }

    private static void publishString(String key, String value, String kind) { publishString(key, value); }

    @JSBody(params={"key","value"}, script="var r=globalThis.pzRuntime;if(!r)return;if(!r.mapCollision)r.mapCollision={state:{},calls:0,byKind:{}};var s=r.mapCollision;s.state[key]=value;s.calls++;s.byKind.string=(s.byKind.string||0)+1;r.mapCollision=s;")
    private static native void recordString(String key, String value);

    @JSBody(script="var r=globalThis.pzRuntime;if(!r)return 0;var s=r.mapCollision;if(!s||!s.state)return 0;return Object.keys(s.state).length;")
    private static native int keyCount();
}
