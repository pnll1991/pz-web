package port.natives;

import java.util.HashMap;
import java.util.Map;
import org.teavm.jso.JSBody;

/**
 * Our own implementation of the Lighting64 JNI library for the browser.
 *
 * The game's zombie.iso.LightingJNI.init() calls System.loadLibrary("Lighting64") and, when that
 * fails, logs the exception, sleeps 3 s and calls System.exit(1), so the library must exist.
 *
 * Round 79: in-world lighting model. This is OUR simplified model, not the original library (its
 * source is not available and is not disassembled). Documented simplifications:
 *  - every square is lit by the frame's sky light (RenderSettings rmod/gmod/bmod * ambient, as passed
 *    to stateEndFrame) plus the registered point lights (addLight/addTempLight/updateTorch) with a
 *    linear falloff and no occlusion; interior/room darkness is not modelled;
 *  - visibility is not line-of-sight based: every square inside the player's view distance is reported
 *    SEEN|CAN_SEE|COULD_SEE (walls do not hide what is behind them);
 *  - the per-square inputs (squareSet*, squareAdd*, chunk/level begin/end) are accepted and counted
 *    but do not change the result, because the simplified model has no occluders.
 * The result layout of getSquareLighting is the one LightingJNI$JNILighting.update() decodes:
 * [vis bits, lightInfo rgb, darkMulti*1e5, targetDarkMulti*1e5, lightLevel rgb, 8 vertex rgb,
 *  lightCount, then id, x, y, z+32, radius, rgb|flags<<24 per light].
 */
public final class Lighting64 {
    private Lighting64() { }
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_RESULT_LIGHTS = 5; // the game's lightInts array has 49 slots
    private static boolean installed;
    private static boolean configured;
    private static boolean destroyed;
    private static float cellSize;

    // Per-player frame state.
    private static final int[] skyRgb = new int[MAX_PLAYERS];
    private static final int[] counter = new int[MAX_PLAYERS];
    private static final float[] viewDistance = new float[MAX_PLAYERS];
    private static final float[] playerX = new float[MAX_PLAYERS];
    private static final float[] playerY = new float[MAX_PLAYERS];
    private static final float[] playerZ = new float[MAX_PLAYERS];
    private static int currentPlayer = -1;
    private static boolean frameChanged;
    private static final Map<Long, Integer>[] chunkSeen = newChunkMaps();
    private static long squareInputs;

    private static final class Light {
        int x, y, z, radius; float r, g, b; boolean active = true; boolean temp;
    }
    private static final Map<Integer, Light> lights = new HashMap<>();
    private static final Map<Integer, Light> torches = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static Map<Long, Integer>[] newChunkMaps() {
        Map<Long, Integer>[] maps = new Map[MAX_PLAYERS];
        for (int i = 0; i < MAX_PLAYERS; i++) maps[i] = new HashMap<>();
        return maps;
    }

    /** Registers the library name so System.loadLibrary("Lighting64") is satisfied by this module. */
    public static void install() {
        if (installed) return;
        registerLibrary("Lighting64");
        installed = true;
        publish("installed");
    }

    public static void configure(float value) {
        if (Float.isNaN(value) || value <= 0.0f)
            throw new IllegalArgumentException("Lighting64.configure: cell size must be a positive float, got " + value);
        if (destroyed) throw new IllegalStateException("Lighting64.configure: library was destroyed");
        cellSize = value;
        configured = true;
        publish("configured");
    }

    /** Our update pass is synchronous, so the module never parks waiting for the main thread. */
    public static boolean waitingForMain() { return false; }

    public static void doLightingUpdate(long now, boolean force) {
        if (!configured) throw new IllegalStateException("Lighting64.DoLightingUpdateNew: configure() has not run yet");
        if (destroyed) throw new IllegalStateException("Lighting64.DoLightingUpdateNew: library was destroyed");
        recordUpdateCall();
        // The simplified model is evaluated lazily in getSquareLighting; there is no deferred work.
    }

    public static void destroy() {
        destroyed = true;
        configured = false;
        cellSize = 0.0f;
        lights.clear(); torches.clear();
        for (Map<Long, Integer> m : chunkSeen) m.clear();
        publish("destroyed");
    }

    // ---- frame state -------------------------------------------------------------------------

    public static void scrollLeft(int player) { }
    public static void scrollRight(int player) { }
    public static void scrollUp(int player) { }
    public static void scrollDown(int player) { }
    public static void teleport(int player, int x, int y) { bump(player); }

    public static void stateBeginUpdate(int player, int minX, int minY, int width, int height) {
        currentPlayer = checkPlayer(player);
        frameChanged = false;
    }

    public static void playerSet(float x, float y, float z, float dx, float dy, boolean a, boolean b, boolean c,
                                 boolean d, float e, float f, float g) {
        if (currentPlayer < 0) return;
        playerX[currentPlayer] = x; playerY[currentPlayer] = y; playerZ[currentPlayer] = z;
    }

    /** rmod, gmod, bmod, ambient, night, viewDistance, viewDistMax, cacheCleared, unused, skyLightLevel. */
    public static void stateEndFrame(float rmod, float gmod, float bmod, float ambient, float night, float view,
                                     float viewMax, boolean cacheCleared, float unused, int skyLight) {
        if (currentPlayer < 0) return;
        int rgb = pack(rmod * ambient, gmod * ambient, bmod * ambient);
        if (rgb != skyRgb[currentPlayer] || cacheCleared) { skyRgb[currentPlayer] = rgb; frameChanged = true; }
        viewDistance[currentPlayer] = Math.max(view, 1.0f);
    }

    public static void stateEndUpdate() {
        if (currentPlayer >= 0 && (frameChanged || counter[currentPlayer] == 0)) counter[currentPlayer]++;
        currentPlayer = -1;
    }

    public static int stateUpdateCounter(int player) { return counter[checkPlayer(player)]; }

    // ---- chunk / square inputs (accepted; the simplified model has no occluders) ------------

    public static boolean chunkLightingDone(int wx, int wy) { return true; }
    public static void chunkBeginUpdate(int a, int b, int c, int d) { squareInputs++; }
    public static void chunkEndUpdate() { }
    public static void chunkLevelBeginUpdate(int z) { }
    public static void chunkLevelEndUpdate() { }
    public static void squareSetNull(int x, int y, int z) { squareInputs++; }
    public static void squareBeginUpdate(int x, int y, int z) { squareInputs++; }
    public static void squareSet(int a, boolean b, boolean c, boolean d, int e, long f, long g, int h, boolean i) { }
    public static void squareSetLightTransmission(float a, float b, float c, float d, float e, float f, float g, float h,
            float i, float j, float k, float l, float m, float n, float o, float p, float q, float r, float s, float t) { }
    public static void squareAddCurtain(int a, boolean b) { }
    public static void squareAddDoor(boolean a, boolean b, boolean c) { }
    public static void squareAddThumpable(boolean a, boolean b, boolean c, boolean d) { }
    public static void squareAddWindow(boolean a, boolean b, boolean c) { }
    public static void squareEndUpdate() { }

    // ---- lights --------------------------------------------------------------------------------

    public static void addLight(int id, int x, int y, int z, int radius, float r, float g, float b, int flags, boolean active) {
        Light l = new Light(); l.x = x; l.y = y; l.z = z; l.radius = radius; l.r = r; l.g = g; l.b = b; l.active = active;
        lights.put(id, l); bumpAll();
    }
    public static void addTempLight(int id, int x, int y, int z, int radius, float r, float g, float b, int flags) {
        Light l = new Light(); l.x = x; l.y = y; l.z = z; l.radius = radius; l.r = r; l.g = g; l.b = b; l.temp = true;
        lights.put(id, l); bumpAll();
    }
    public static void removeLight(int id) { if (lights.remove(id) != null) bumpAll(); }
    public static void setLightActive(int id, boolean active) { Light l = lights.get(id); if (l != null && l.active != active) { l.active = active; bumpAll(); } }
    public static void setLightColor(int id, float r, float g, float b) { Light l = lights.get(id); if (l != null) { l.r = r; l.g = g; l.b = b; bumpAll(); } }
    /** Room lights light a whole room; rooms are not modelled, so they are recorded only. */
    public static void addRoomLight(int id, long building, long room, int x, int y, int z, int w, int h, boolean active) { }
    public static void removeRoomLight(int id) { }
    public static void setRoomLightActive(int id, boolean active) { }
    public static void updateTorch(int id, float x, float y, float z, float angleX, float angleY, float dist, float strength,
                                   float r, float g, float b, boolean cone, float dot, int flags) {
        Light l = torches.get(id);
        if (l == null) torches.put(id, l = new Light());
        int nx = (int) Math.floor(x), ny = (int) Math.floor(y), nz = (int) Math.floor(z), nr = Math.max(1, Math.round(dist));
        if (l.x != nx || l.y != ny || l.z != nz || l.radius != nr) bumpAll();
        l.x = nx; l.y = ny; l.z = nz; l.radius = nr; l.r = r * strength; l.g = g * strength; l.b = b * strength;
    }
    public static void removeTorch(int id) { if (torches.remove(id) != null) bumpAll(); }

    public static int getVisibleRoomCount(int player) { return 0; }
    public static int getVisibleRooms(int player, long[] out) { return 0; }

    // ---- queries -----------------------------------------------------------------------------

    public static boolean getChunkDirty(int player, int wx, int wy, int z) {
        int p = checkPlayer(player);
        long key = (((long) wx) << 40) ^ (((long) wy) << 16) ^ (z & 0xffff);
        Integer seen = chunkSeen[p].put(key, counter[p]);
        return seen == null || seen != counter[p];
    }

    public static boolean getSquareDirty(int player, int x, int y, int z) { return true; }

    public static boolean getSquareLighting(int player, int x, int y, int z, int[] out) {
        int p = checkPlayer(player);
        int level = z - 32;
        float view = viewDistance[p];
        float dx = x + 0.5f - playerX[p], dy = y + 0.5f - playerY[p];
        boolean inView = dx * dx + dy * dy <= view * view;
        int rgb = lit(p, x, y, level);
        int i = 0;
        out[i++] = inView ? 7 : 1;   // SEEN | CAN_SEE | COULD_SEE
        out[i++] = rgb;
        out[i++] = 100000;           // darkMulti 1.0
        out[i++] = 100000;           // targetDarkMulti 1.0
        out[i++] = rgb;              // lightLevel
        for (int v = 0; v < 8; v++) out[i++] = rgb;
        int countAt = i++, count = 0;
        count = appendLights(out, i, count, lights, x, y, level);
        count = appendLights(out, i + count * 6, count, torches, x, y, level);
        out[countAt] = count;
        return true;
    }

    public static int getVertLight(int player, int vertex, int x, int y, int z) { return lit(checkPlayer(player), x, y, z - 32) | 0xff000000; }
    public static float getLightInfo(int player, int x, int y, int z, int channel) {
        int rgb = lit(checkPlayer(player), x, y, z - 32);
        return ((rgb >> (channel * 8)) & 0xff) / 255.0f;
    }
    public static float getDarkMulti(int player, int x, int y, int z) { return 1.0f; }
    public static float getTargetDarkMulti(int player, int x, int y, int z) { return 1.0f; }
    public static boolean getSeen(int player, int x, int y, int z) { return true; }
    public static boolean getCanSee(int player, int x, int y, int z) { return true; }
    public static boolean getCouldSee(int player, int x, int y, int z) { return true; }

    // ---- model ---------------------------------------------------------------------------------

    private static int lit(int p, int x, int y, int z) {
        int sky = skyRgb[p];
        float r = (sky & 0xff) / 255.0f, g = ((sky >> 8) & 0xff) / 255.0f, b = ((sky >> 16) & 0xff) / 255.0f;
        float[] acc = { r, g, b };
        addLights(acc, lights, x, y, z);
        addLights(acc, torches, x, y, z);
        return pack(acc[0], acc[1], acc[2]);
    }

    private static void addLights(float[] acc, Map<Integer, Light> set, int x, int y, int z) {
        if (set.isEmpty()) return;
        for (Light l : set.values()) {
            if (!l.active || l.z != z || l.radius <= 0) continue;
            float ddx = x - l.x, ddy = y - l.y, d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (d >= l.radius) continue;
            float k = 1.0f - d / l.radius;
            acc[0] += l.r * k; acc[1] += l.g * k; acc[2] += l.b * k;
        }
    }

    private static int appendLights(int[] out, int at, int count, Map<Integer, Light> set, int x, int y, int z) {
        for (Map.Entry<Integer, Light> e : set.entrySet()) {
            if (count >= MAX_RESULT_LIGHTS) break;
            Light l = e.getValue();
            if (!l.active || l.z != z) continue;
            int ddx = x - l.x, ddy = y - l.y;
            if (ddx * ddx + ddy * ddy >= l.radius * l.radius) continue;
            out[at++] = e.getKey(); out[at++] = l.x; out[at++] = l.y; out[at++] = l.z + 32; out[at++] = l.radius;
            out[at++] = pack(l.r, l.g, l.b);
            count++;
        }
        return count;
    }

    private static int pack(float r, float g, float b) {
        return channel(r) | (channel(g) << 8) | (channel(b) << 16);
    }
    private static int channel(float v) { return Math.max(0, Math.min(255, Math.round(v * 255.0f))); }

    private static void bump(int player) { counter[checkPlayer(player)]++; }
    private static void bumpAll() { for (int i = 0; i < MAX_PLAYERS; i++) counter[i]++; }
    private static int checkPlayer(int player) {
        if (player < 0 || player >= MAX_PLAYERS) throw new IllegalArgumentException("Lighting64: player index " + player);
        return player;
    }

    public static boolean isInstalled() { return installed; }
    public static boolean isConfigured() { return configured; }
    public static float cellSize() { return cellSize; }

    @JSBody(params="library",script="var r=globalThis.pzRuntime;if(!r)return;var n=r.loadedLibraries||[];if(n.indexOf(library)<0)n.push(library);r.loadedLibraries=n;if(!r.natives)r.natives={};var s=r.natives[library];if(!s||typeof s!=='object')r.natives[library]={stage:'registered',configureCalls:0,updateCalls:0};")
    private static native void registerLibrary(String library);

    /** Diagnostic state, so a test can prove the module answered instead of a stub. */
    @JSBody(params="stage",script="var r=globalThis.pzRuntime;if(!r)return;if(!r.natives)r.natives={};var s=r.natives.Lighting64;if(!s||typeof s!=='object'){s={stage:'',configureCalls:0,updateCalls:0};r.natives.Lighting64=s;}s.stage=stage;s.configureCalls=s.configureCalls+(stage==='configured'?1:0);r.natives.Lighting64=s;")
    private static native void publish(String stage);

    @JSBody(script="var r=globalThis.pzRuntime;if(!r)return;if(!r.natives)r.natives={};var s=r.natives.Lighting64;if(!s||typeof s!=='object'){s={stage:'',configureCalls:0,updateCalls:0};r.natives.Lighting64=s;}s.updateCalls++;r.natives.Lighting64=s;")
    private static native void recordUpdateCall();
}
