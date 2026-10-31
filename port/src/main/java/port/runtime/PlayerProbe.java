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
}
