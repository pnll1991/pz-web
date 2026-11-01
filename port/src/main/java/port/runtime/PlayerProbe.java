package port.runtime;

import java.util.List;
import org.teavm.jso.JSBody;

/**
 * Ronda 80 (frente M): sonda de sólo lectura del jugador local, opt-in.
 *
 * {@code port.compiler.PlayerProbeCompatibility} inyecta {@link #frame()} antes de cada retorno de
 * {@code zombie.gameStates.IngameState#update()} y reescribe cada stub {@code g*} de abajo con IR directa
 * (getstatic / invokevirtual / invokestatic sobre las clases del jar fijado), igual que JassimpObjects:
 * nuestras fuentes no compilan contra el jar, así que los tipos del juego son {@code Object} aquí.
 * Sólo se llaman getters públicos sin efectos (verificados con {@code javap -c}); en particular
 * {@code getDeferredMovement(Vector2)} pasa {@code reset=false} y {@code getInputMoveVector} NO se usa
 * (llama a {@code setAutoWalkDirection}). No cambia ninguna regla ni estado del juego.
 *
 * Costo apagado: un contador y una llamada JS por frame que devuelve un booleano cacheado (el flag
 * {@code globalThis.PZ_PLAYER_PROBE === true} o {@code ?probe=1} se relee como mucho una vez por segundo).
 * Encendido: publica {@code globalThis.pzRuntime.playerProbe} (objeto JSON) en cada frame de juego.
 */
public final class PlayerProbe {
    private PlayerProbe() { }

    private static int frames;
    private static int samples;
    private static boolean started;
    private static float x0, y0, lastX, lastY, maxDist;
    private static int forwardFrames, movingFrames, animUpdatingFrames, deferredNonZeroFrames, translatedFrames;
    private static String lastError = "";

    /** Inyectado al final de IngameState#update(); no debe lanzar nunca. */
    public static void frame() {
        frames++;
        if (!enabled()) return;
        try {
            publish(sample());
        } catch (Throwable error) {
            lastError = String.valueOf(error);
            publish("{\"frames\":" + frames + ",\"error\":" + quote(lastError) + "}");
        }
    }

    private static String sample() {
        samples++;
        var j = new Json();
        j.num("frames", frames).num("samples", samples);
        Object ingame = gIngameInstance();
        if (ingame != null) j.num("numberTicks", gIngameTicks(ingame)).bool("ingamePaused", gIngamePaused(ingame));
        j.bool("gameTimePaused", gIsGamePaused());
        Object time = gGameTimeInstance();
        if (time != null) j.num("multiplier", gMultiplier(time)).num("trueMultiplier", gTrueMultiplier(time));
        Object speed = gSpeedControls();
        j.num("gameSpeed", speed == null ? -1 : gCurrentGameSpeed(speed));
        j.bool("showPausedMessage", gShowPausedMessage());
        Object core = gCoreInstance();
        if (core != null) j.bool("doingTextEntry", gDoingTextEntry(core));

        Object forward = gKeyForward(), right = gKeyRight();
        int forwardKey = core == null ? -1 : gKeybindKey(core, forward);
        int rightKey = core == null ? -1 : gKeybindKey(core, right);
        boolean forwardDown = gKeybindDown(forward);
        j.str("forwardId", gKeybindId(forward)).num("forwardKey", forwardKey).bool("forwardDown", forwardDown);
        j.str("rightId", gKeybindId(right)).num("rightKey", rightKey).bool("rightDown", gKeybindDown(right));
        if (forwardKey > 0) j.bool("gameKeyboardForwardKey", gGameKeyboardDown(forwardKey));
        j.bool("gameKeyboard17", gGameKeyboardDown(17)).bool("gameKeyboard32", gGameKeyboardDown(32));
        j.bool("lwjglKeyboard17", gLwjglKeyboardDown(17)).bool("lwjglKeyboard32", gLwjglKeyboardDown(32));
        if (forwardDown) forwardFrames++;

        j.num("numPlayers", gNumPlayers());
        Object[] players = (Object[]) gPlayers();
        Object player = players == null || players.length == 0 ? null : players[0];
        j.bool("player0", player != null);
        if (player != null) samplePlayer(j, player);
        j.num("forwardFrames", forwardFrames).num("movingFrames", movingFrames)
            .num("animUpdatingFrames", animUpdatingFrames).num("deferredNonZeroFrames", deferredNonZeroFrames)
            .num("translatedFrames", translatedFrames).num("maxDist", maxDist);
        if (!lastError.isEmpty()) j.str("lastError", lastError);
        return j.end();
    }

    private static void samplePlayer(Json j, Object p) {
        float x = gX(p), y = gY(p), z = gZ(p);
        if (!started) { started = true; x0 = x; y0 = y; lastX = x; lastY = y; }
        if (x != lastX || y != lastY) translatedFrames++;
        lastX = x; lastY = y;
        float dx = x - x0, dy = y - y0, dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > maxDist) maxDist = dist;
        j.num("x", x).num("y", y).num("z", z).num("dist", dist);
        j.num("playerNum", gPlayerNum(p)).bool("localPlayer", gLocalPlayer(p)).bool("npc", gNpc(p));
        j.num("fwdX", gForwardX(p)).num("fwdY", gForwardY(p));
        Object mv = gMoveForwardVec(p);
        if (mv != null) j.num("moveFwdX", gVecX(mv)).num("moveFwdY", gVecY(mv));
        boolean moving = gIsMoving(p), animUpdating = gAnimUpdating(p);
        if (moving) movingFrames++;
        if (animUpdating) animUpdatingFrames++;
        j.bool("isMoving", moving).bool("isPlayerMoving", gIsPlayerMoving(p)).bool("isWalking", gIsWalking(p))
            .bool("isRunning", gIsRunning(p)).bool("isSprinting", gIsSprinting(p)).bool("isAiming", gIsAiming(p))
            .bool("autoWalk", gAutoWalk(p)).bool("blockMovement", gBlockMovement(p))
            .bool("performingAction", gPerformingAction(p)).bool("pathfindRunning", gPathfindRunning(p))
            .bool("ghost", gGhost(p)).bool("dead", gDead(p)).bool("asleep", gAsleep(p))
            .bool("falling", gFalling(p)).bool("sitOnGround", gSitOnGround(p));
        j.num("moveSpeed", gMoveSpeed(p)).num("pathSpeed", gPathSpeed(p));
        j.str("currentState", gCurrentStateName(p)).str("actionState", gActionStateName(p))
            .str("actionContextState", gActionContextStateName(p));
        boolean hasPlayer = gHasAnimationPlayer(p);
        j.bool("hasAnimationPlayer", hasPlayer).bool("animUpdatingThisFrame", animUpdating);
        Object deferred = gDeferredMovement(p);
        if (deferred != null) {
            float ddx = gVecX(deferred), ddy = gVecY(deferred);
            if (ddx != 0 || ddy != 0) deferredNonZeroFrames++;
            j.num("deferredX", ddx).num("deferredY", ddy).num("deferredLen", (float) Math.sqrt(ddx * ddx + ddy * ddy));
        }
        try {
            sampleAnimation(j, p);
        } catch (Throwable error) {
            j.str("animationError", String.valueOf(error));
        }
        Object animator = gAdvancedAnimator(p);
        j.bool("advancedAnimator", animator != null);
        if (animator == null) return;
        j.str("animatorState", gAnimatorStateName(animator));
        var layers = new StringBuilder("[");
        Object root = gRootLayer(animator);
        if (root != null) layer(layers, "root", root);
        int subs = gSubLayerCount(animator);
        for (int i = 0; i < subs && i < 8; i++) {
            Object sub = gSubLayerAt(animator, i);
            if (sub != null) { layers.append(','); layer(layers, "sub" + i, sub); }
        }
        j.raw("subLayerCount", Integer.toString(subs)).raw("layers", layers.append(']').toString());
    }

    // ---------------------------------------------------------------- frente P: pistas, reloj y huesos
    private static final String[] BONES = {"Dummy01", "Translation_Data", "Bip01", "Bip01_Pelvis", "Bip01_Spine"};
    private static long lastNanos, lastMillis;
    private static float lastTrack0Time = Float.NaN;
    private static int track0AdvancedFrames, track0StuckFrames, track0Wraps;

    /** Reloj del puerto y de GameTime: el delta que avanza las pistas (getAnimationTimeDelta). */
    private static void sampleClock(Json j, Object p) {
        long nanos = System.nanoTime(), millis = System.currentTimeMillis();
        j.num("nanoTime", nanos).num("nanoDeltaMs", lastNanos == 0 ? 0 : (nanos - lastNanos) / 1e6)
            .num("currentTimeMillis", millis).num("millisDelta", lastMillis == 0 ? 0 : millis - lastMillis);
        lastNanos = nanos; lastMillis = millis;
        Object time = gGameTimeInstance();
        if (time != null) {
            j.num("timeDelta", gTimeDelta(time)).num("fpsMultiplier", gFpsMultiplier(time))
                .num("perObjectMultiplier", gPerObjectMultiplier(time)).num("multiplierBias", gMultiplierBias(time));
        }
        j.num("slomo", gSlomo()).num("averageFPS", gAverageFps()).num("animTimeDelta", gAnimTimeDelta(p));
    }

    /** AnimationPlayer del ModelInstance (campo público, sin el alloc de getAnimationPlayer()). */
    private static void sampleAnimation(Json j, Object p) {
        sampleClock(j, p);
        Object instance = gModelInstance(p);
        j.bool("modelInstance", instance != null);
        if (instance == null) return;
        Object ap = gInstanceAnimPlayer(instance);
        j.bool("animPlayer", ap != null);
        if (ap == null) return;
        j.num("apAngle", gApAngle(ap)).num("boneTransformsTimeDelta", gBoneTransformsTimeDelta(ap));
        Object mt = gMultiTrack(ap);
        int count = mt == null ? 0 : gTrackCount(mt);
        var tracks = new StringBuilder("[");
        for (int i = 0; i < count && i < 6; i++) {
            Object t = gTrackAt(mt, i);
            if (i > 0) tracks.append(',');
            if (t == null) { tracks.append("null"); continue; }
            float cur = gTrackCur(t), prev = gTrackPrev(t);
            if (i == 0) {
                if (!Float.isNaN(lastTrack0Time)) {
                    if (cur > lastTrack0Time) track0AdvancedFrames++;
                    else if (cur == lastTrack0Time) track0StuckFrames++;
                    else track0Wraps++;
                }
                lastTrack0Time = cur;
            }
            var tj = new Json();
            tj.str("name", gTrackName(t)).num("cur", cur).num("prev", prev).num("dur", gTrackDur(t))
                .num("acc", gTrackAcc(t)).bool("playing", gTrackPlaying(t)).bool("looping", gTrackLooping(t))
                .bool("reverse", gTrackReverse(t)).bool("finished", gTrackFinished(t)).num("speed", gTrackSpeed(t))
                .num("weight", gTrackWeight(t)).num("layer", gTrackLayer(t)).num("deferredBone", gTrackDeferredBone(t))
                .bool("useDeferred", gTrackUseDeferred(t));
            Object clip = gTrackClip(t);
            if (clip != null) {
                Object[] all = (Object[]) gClipKeyframes(clip);
                tj.num("clipDur", gClipDuration(clip)).num("clipKeyframes", all == null ? -1 : all.length);
                int deferred = gTrackDeferredBone(t);
                if (deferred >= 0) tj.raw("deferredFrames", frames(clip, deferred));
                if (i == 0) tj.raw("bone0Frames", frames(clip, 0));
            }
            tracks.append(tj.end());
        }
        j.num("trackCount", count).raw("tracks", tracks.append(']').toString());
        j.num("track0AdvancedFrames", track0AdvancedFrames).num("track0StuckFrames", track0StuckFrames)
            .num("track0Wraps", track0Wraps);

        Object sd = gSkinningData(ap);
        List<?> hierarchy = null, bindPose = null, boneOffset = null;
        if (sd != null) {
            hierarchy = (List<?>) gSkeletonHierarchy(sd);
            bindPose = (List<?>) gBindPose(sd);
            boneOffset = (List<?>) gBoneOffset(sd);
            if (hierarchy != null) {
                int roots = 0;
                var parents = new StringBuilder("[");
                for (int i = 0; i < hierarchy.size(); i++) {
                    Object parent = hierarchy.get(i);
                    if (parent instanceof Integer n && n < 0) roots++;
                    if (i < 40) { if (i > 0) parents.append(','); parents.append(parent); }
                }
                j.num("skeletonBones", hierarchy.size()).num("skeletonRoots", roots).raw("parents", parents.append(']').toString());
            }
        }
        int transforms = gModelTransformsCount(ap);
        j.num("modelTransforms", transforms);
        var store = new float[16];
        var buffer = java.nio.FloatBuffer.wrap(store);
        // Todas las matrices de modelo: NaN y determinantes (col-major, como store()).
        int nan = 0, degenerate = 0;
        float detMin = Float.POSITIVE_INFINITY, detMax = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < transforms; i++) {
            Object m = gModelTransformAt(ap, i);
            if (m == null) continue;
            buffer.clear(); gMatrixStore(m, buffer);
            for (float v : store) if (!Float.isFinite(v)) nan++;
            float d = det(store);
            if (Math.abs(d) < 1e-4f) degenerate++;
            if (d < detMin) detMin = d;
            if (d > detMax) detMax = d;
        }
        j.num("modelNaN", nan).num("modelDegenerate", degenerate).num("modelDetMin", detMin).num("modelDetMax", detMax);
        var bones = new StringBuilder("[");
        for (int b = 0; b < BONES.length; b++) {
            int index = gBoneIndex(ap, BONES[b], -1);
            if (b > 0) bones.append(',');
            var bj = new Json();
            bj.str("bone", BONES[b]).num("index", index);
            if (index >= 0 && index < transforms) {
                Object m = gModelTransformAt(ap, index);
                if (m != null) { buffer.clear(); gMatrixStore(m, buffer); bj.raw("model", matrix(store)); }
                Object local = gBoneLocal(ap, index, gNewMatrix());
                if (local != null) { buffer.clear(); gMatrixStore(local, buffer); bj.raw("local", matrix(store)); }
                if (bindPose != null && index < bindPose.size() && bindPose.get(index) != null) {
                    buffer.clear(); gMatrixStore(bindPose.get(index), buffer); bj.raw("bindPose", matrix(store));
                }
                if (boneOffset != null && index < boneOffset.size() && boneOffset.get(index) != null) {
                    buffer.clear(); gMatrixStore(boneOffset.get(index), buffer); bj.raw("boneOffset", matrix(store));
                }
            }
            bones.append(bj.end());
        }
        j.raw("bones", bones.append(']').toString());
    }

    /** Claves de un hueso en el clip: cantidad, primer/último tiempo, primera rotación (x,y,z,w) y posición. */
    private static String frames(Object clip, int bone) {
        Object[] keys;
        try { keys = (Object[]) gClipBoneFrames(clip, bone); } catch (Throwable e) { return quote(String.valueOf(e)); }
        if (keys == null) return "null";
        var out = new Json();
        out.num("n", keys.length);
        if (keys.length > 0 && keys[0] != null && keys[keys.length - 1] != null) {
            Object k0 = keys[0];
            out.num("t0", gKeyTime(k0)).num("tLast", gKeyTime(keys[keys.length - 1]));
            Object q = gKeyRot(k0), v = gKeyPos(k0), vl = gKeyPos(keys[keys.length - 1]);
            if (q != null) out.raw("rot0", "[" + num(gQx(q)) + "," + num(gQy(q)) + "," + num(gQz(q)) + "," + num(gQw(q)) + "]");
            if (v != null) out.raw("pos0", "[" + num(gV3x(v)) + "," + num(gV3y(v)) + "," + num(gV3z(v)) + "]");
            if (vl != null) out.raw("posLast", "[" + num(gV3x(vl)) + "," + num(gV3y(vl)) + "," + num(gV3z(vl)) + "]");
        }
        return out.end();
    }

    private static String matrix(float[] m) {
        var b = new StringBuilder("[");
        for (int i = 0; i < 16; i++) { if (i > 0) b.append(','); b.append(num(Math.round(m[i] * 1e4f) / 1e4f)); }
        return b.append(']').toString();
    }

    /** Determinante de una 4x4 (el orden fila/columna no lo cambia). */
    static float det(float[] m) {
        float a0 = m[0] * m[5] - m[1] * m[4], a1 = m[0] * m[6] - m[2] * m[4], a2 = m[0] * m[7] - m[3] * m[4];
        float a3 = m[1] * m[6] - m[2] * m[5], a4 = m[1] * m[7] - m[3] * m[5], a5 = m[2] * m[7] - m[3] * m[6];
        float b0 = m[8] * m[13] - m[9] * m[12], b1 = m[8] * m[14] - m[10] * m[12], b2 = m[8] * m[15] - m[11] * m[12];
        float b3 = m[9] * m[14] - m[10] * m[13], b4 = m[9] * m[15] - m[11] * m[13], b5 = m[10] * m[15] - m[11] * m[14];
        return a0 * b5 - a1 * b4 + a2 * b3 + a3 * b2 - a4 * b1 + a5 * b0;
    }

    private static void layer(StringBuilder out, String label, Object layer) {
        out.append("{\"layer\":").append(quote(label))
            .append(",\"state\":").append(quote(gLayerStateName(layer)))
            .append(",\"debugNode\":").append(quote(gLayerDebugNodeName(layer))).append(",\"nodes\":[");
        Object nodes = gLiveAnimNodes(layer);
        if (nodes instanceof List<?> list) {
            for (int i = 0; i < list.size() && i < 8; i++) {
                Object node = list.get(i);
                if (i > 0) out.append(',');
                if (node == null) { out.append("null"); continue; }
                out.append("{\"name\":").append(quote(gNodeName(node)))
                    .append(",\"w\":").append(num(gNodeWeight(node)))
                    .append(",\"active\":").append(gNodeActive(node)).append('}');
            }
        }
        out.append("]}");
    }

    // ---------------------------------------------------------------- JSON mínimo
    private static final class Json {
        private final StringBuilder out = new StringBuilder("{");
        private Json key(String k) { if (out.length() > 1) out.append(','); out.append('"').append(k).append("\":"); return this; }
        Json num(String k, double v) { key(k).out.append(PlayerProbe.num(v)); return this; }
        Json bool(String k, boolean v) { key(k).out.append(v); return this; }
        Json str(String k, String v) { key(k).out.append(quote(v)); return this; }
        Json raw(String k, String v) { key(k).out.append(v); return this; }
        String end() { return out.append('}').toString(); }
    }
    private static String num(double v) { return Double.isFinite(v) ? Double.toString(v) : "null"; }
    private static String quote(String s) {
        if (s == null) return "null";
        var b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(' ');
            else b.append(c);
        }
        return b.append('"').toString();
    }

    // ---------------------------------------------------------------- JS
    @JSBody(script = """
      var g=globalThis, s=g.__pzPlayerProbeFlag||(g.__pzPlayerProbeFlag={t:0,v:false});
      var n=performance.now(); if(n<s.t)return s.v; s.t=n+1000;
      s.v=g.PZ_PLAYER_PROBE===true
        ||(typeof location!=='undefined'&&new URLSearchParams(location.search||'').get('probe')==='1');
      return s.v;
    """)
    private static native boolean enabled();

    @JSBody(params = "json", script = """
      var r=globalThis.pzRuntime||(globalThis.pzRuntime={});
      try{r.playerProbe=JSON.parse(json);}catch(e){r.playerProbe={parseError:String(e),raw:json};}
      r.playerProbe.time=performance.now()|0;
    """)
    private static native void publish(String json);

    // ---------------------------------------------------------------- stubs reescritos por el compilador
    // Cada cuerpo se reemplaza con IR por PlayerProbeCompatibility (la tabla de allí manda); el build
    // falla si un stub queda sin reescribir o si el método del juego no existe en el jar.
    private static RuntimeException stub() { return new IllegalStateException("PlayerProbe stub not rewritten"); }
    static Object gIngameInstance() { throw stub(); }
    static long gIngameTicks(Object s) { throw stub(); }
    static boolean gIngamePaused(Object s) { throw stub(); }
    static boolean gIsGamePaused() { throw stub(); }
    static Object gGameTimeInstance() { throw stub(); }
    static float gMultiplier(Object t) { throw stub(); }
    static float gTrueMultiplier(Object t) { throw stub(); }
    static Object gSpeedControls() { throw stub(); }
    static int gCurrentGameSpeed(Object s) { throw stub(); }
    static boolean gShowPausedMessage() { throw stub(); }
    static Object gCoreInstance() { throw stub(); }
    static boolean gDoingTextEntry(Object c) { throw stub(); }
    static Object gKeyForward() { throw stub(); }
    static Object gKeyRight() { throw stub(); }
    static int gKeybindKey(Object core, Object keybind) { throw stub(); }
    static boolean gKeybindDown(Object keybind) { throw stub(); }
    static String gKeybindId(Object keybind) { throw stub(); }
    static boolean gGameKeyboardDown(int key) { throw stub(); }
    static boolean gLwjglKeyboardDown(int key) { throw stub(); }
    static int gNumPlayers() { throw stub(); }
    static Object gPlayers() { throw stub(); }
    static float gX(Object p) { throw stub(); }
    static float gY(Object p) { throw stub(); }
    static float gZ(Object p) { throw stub(); }
    static int gPlayerNum(Object p) { throw stub(); }
    static boolean gLocalPlayer(Object p) { throw stub(); }
    static boolean gNpc(Object p) { throw stub(); }
    static float gForwardX(Object p) { throw stub(); }
    static float gForwardY(Object p) { throw stub(); }
    static Object gMoveForwardVec(Object p) { throw stub(); }
    static float gVecX(Object v) { throw stub(); }
    static float gVecY(Object v) { throw stub(); }
    static boolean gIsMoving(Object p) { throw stub(); }
    static boolean gIsPlayerMoving(Object p) { throw stub(); }
    static boolean gIsWalking(Object p) { throw stub(); }
    static boolean gIsRunning(Object p) { throw stub(); }
    static boolean gIsSprinting(Object p) { throw stub(); }
    static boolean gIsAiming(Object p) { throw stub(); }
    static boolean gAutoWalk(Object p) { throw stub(); }
    static boolean gBlockMovement(Object p) { throw stub(); }
    static boolean gPerformingAction(Object p) { throw stub(); }
    static boolean gPathfindRunning(Object p) { throw stub(); }
    static boolean gGhost(Object p) { throw stub(); }
    static boolean gDead(Object p) { throw stub(); }
    static boolean gAsleep(Object p) { throw stub(); }
    static boolean gFalling(Object p) { throw stub(); }
    static boolean gSitOnGround(Object p) { throw stub(); }
    static float gMoveSpeed(Object p) { throw stub(); }
    static float gPathSpeed(Object p) { throw stub(); }
    static String gCurrentStateName(Object p) { throw stub(); }
    static String gActionStateName(Object p) { throw stub(); }
    static String gActionContextStateName(Object p) { throw stub(); }
    static boolean gHasAnimationPlayer(Object p) { throw stub(); }
    static boolean gAnimUpdating(Object p) { throw stub(); }
    static Object gDeferredMovement(Object p) { throw stub(); }
    static Object gAdvancedAnimator(Object p) { throw stub(); }
    static String gAnimatorStateName(Object a) { throw stub(); }
    static Object gRootLayer(Object a) { throw stub(); }
    static int gSubLayerCount(Object a) { throw stub(); }
    static Object gSubLayerAt(Object a, int i) { throw stub(); }
    static String gLayerStateName(Object l) { throw stub(); }
    static String gLayerDebugNodeName(Object l) { throw stub(); }
    static Object gLiveAnimNodes(Object l) { throw stub(); }
    static String gNodeName(Object n) { throw stub(); }
    static float gNodeWeight(Object n) { throw stub(); }
    static boolean gNodeActive(Object n) { throw stub(); }
    // frente P
    static float gTimeDelta(Object t) { throw stub(); }
    static float gFpsMultiplier(Object t) { throw stub(); }
    static float gPerObjectMultiplier(Object t) { throw stub(); }
    static float gMultiplierBias(Object t) { throw stub(); }
    static float gSlomo() { throw stub(); }
    static float gAverageFps() { throw stub(); }
    static float gAnimTimeDelta(Object p) { throw stub(); }
    static Object gModelInstance(Object p) { throw stub(); }
    static Object gInstanceAnimPlayer(Object mi) { throw stub(); }
    static float gApAngle(Object ap) { throw stub(); }
    static float gBoneTransformsTimeDelta(Object ap) { throw stub(); }
    static Object gMultiTrack(Object ap) { throw stub(); }
    static int gTrackCount(Object mt) { throw stub(); }
    static Object gTrackAt(Object mt, int i) { throw stub(); }
    static String gTrackName(Object t) { throw stub(); }
    static float gTrackCur(Object t) { throw stub(); }
    static float gTrackPrev(Object t) { throw stub(); }
    static float gTrackDur(Object t) { throw stub(); }
    static float gTrackAcc(Object t) { throw stub(); }
    static boolean gTrackPlaying(Object t) { throw stub(); }
    static boolean gTrackLooping(Object t) { throw stub(); }
    static boolean gTrackReverse(Object t) { throw stub(); }
    static boolean gTrackFinished(Object t) { throw stub(); }
    static float gTrackSpeed(Object t) { throw stub(); }
    static float gTrackWeight(Object t) { throw stub(); }
    static int gTrackLayer(Object t) { throw stub(); }
    static int gTrackDeferredBone(Object t) { throw stub(); }
    static boolean gTrackUseDeferred(Object t) { throw stub(); }
    static Object gTrackClip(Object t) { throw stub(); }
    static float gClipDuration(Object c) { throw stub(); }
    static Object gClipKeyframes(Object c) { throw stub(); }
    static Object gClipBoneFrames(Object c, int bone) { throw stub(); }
    static float gKeyTime(Object k) { throw stub(); }
    static Object gKeyRot(Object k) { throw stub(); }
    static Object gKeyPos(Object k) { throw stub(); }
    static float gQx(Object q) { throw stub(); }
    static float gQy(Object q) { throw stub(); }
    static float gQz(Object q) { throw stub(); }
    static float gQw(Object q) { throw stub(); }
    static float gV3x(Object v) { throw stub(); }
    static float gV3y(Object v) { throw stub(); }
    static float gV3z(Object v) { throw stub(); }
    static int gModelTransformsCount(Object ap) { throw stub(); }
    static Object gModelTransformAt(Object ap, int i) { throw stub(); }
    static int gBoneIndex(Object ap, String name, int fallback) { throw stub(); }
    static Object gNewMatrix() { throw stub(); }
    static Object gBoneLocal(Object ap, int i, Object m) { throw stub(); }
    static Object gMatrixStore(Object m, Object floatBuffer) { throw stub(); }
    static Object gSkinningData(Object ap) { throw stub(); }
    static Object gSkeletonHierarchy(Object sd) { throw stub(); }
    static Object gBindPose(Object sd) { throw stub(); }
    static Object gBoneOffset(Object sd) { throw stub(); }
}
