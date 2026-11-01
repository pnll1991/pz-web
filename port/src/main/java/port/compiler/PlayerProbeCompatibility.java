package port.compiler;

import java.util.HashMap;
import java.util.Map;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * Ronda 80 (frente M): sonda opt-in del jugador (docs/notes-from-code.md, "frente M").
 *
 * 1. Antes de cada retorno de {@code zombie.gameStates.IngameState#update()} inyecta
 *    {@code port.runtime.PlayerProbe.frame()} (sin argumentos: nada que materializar en un frame reanudado).
 * 2. Reescribe cada stub {@code g*} de {@code port.runtime.PlayerProbe} con una única lectura sobre la
 *    clase del juego (getstatic/getfield/invokestatic/invokevirtual de getters públicos; descriptores de
 *    {@code javap -s} sobre el jar fijado). El build falla si falta un stub, una reescritura o el miembro
 *    del juego, en lugar de compilar una sonda que mienta.
 * Es diagnóstico de sólo lectura: no cambia reglas ni estado del juego.
 */
public final class PlayerProbeCompatibility implements ClassHolderTransformer {
    private static final String PROBE = "port.runtime.PlayerProbe";
    private static final String INGAME = "zombie.gameStates.IngameState";
    private static final String PLAYER = "zombie.characters.IsoPlayer";
    private static final String VEC2 = "zombie.iso.Vector2";
    private static final String ANIMATOR = "zombie.core.skinnedmodel.advancedanimation.AdvancedAnimator";
    private static final String LAYER = "zombie.core.skinnedmodel.advancedanimation.AnimLayer";
    private static final String LIVE_NODE = "zombie.core.skinnedmodel.advancedanimation.LiveAnimNode";
    private static final String KEYBIND = "zombie.input.KeybindId";
    private static final String MODEL_INSTANCE = "zombie.core.skinnedmodel.model.ModelInstance";
    private static final String ANIM_PLAYER = "zombie.core.skinnedmodel.animation.AnimationPlayer";
    private static final String MULTI_TRACK = "zombie.core.skinnedmodel.animation.AnimationMultiTrack";
    private static final String TRACK = "zombie.core.skinnedmodel.animation.AnimationTrack";
    private static final String CLIP = "zombie.core.skinnedmodel.animation.AnimationClip";
    private static final String KEYFRAME = "zombie.core.skinnedmodel.animation.Keyframe";
    private static final String QUAT = "org.lwjgl.util.vector.Quaternion";
    private static final String VEC3 = "org.lwjgl.util.vector.Vector3f";
    private static final String MATRIX = "org.lwjgl.util.vector.Matrix4f";
    private static final ValueType OBJ = ValueType.object("java.lang.Object");
    private static final ValueType STR = ValueType.object("java.lang.String");
    private static final ValueType Z = ValueType.BOOLEAN, I = ValueType.INTEGER, F = ValueType.FLOAT, J = ValueType.LONG;
    private static ValueType t(String cls) { return ValueType.object(cls); }

    private interface Body { ValueEmitter emit(E e); }

    private static final class E {
        final ProgramEmitter pe; final MethodHolder method; final ClassHolderTransformerContext context;
        E(ProgramEmitter pe, MethodHolder method, ClassHolderTransformerContext context) {
            this.pe = pe; this.method = method; this.context = context;
        }
        // Búsqueda directa en el ClassSource (como JassimpNativeCompatibility): ClassHierarchy.resolve(FieldReference)
        // devolvió null para IngameState.instance durante la transformación (build-round80m-dev-game.log).
        org.teavm.model.ClassReader cls(String name) {
            var c = context.getHierarchy().getClassSource().get(name);
            if (c == null) throw new IllegalStateException("PlayerProbe: game class missing: " + name);
            return c;
        }
        ValueEmitter arg(int i) { return pe.var(i + 1, method.parameterType(i)); }
        MethodReference ref(String owner, String name, boolean isStatic, ValueType... signature) {
            var ref = new MethodReference(owner, name, signature);
            org.teavm.model.MethodReader found = null;
            for (var c = cls(owner); c != null && found == null; c = c.getParent() == null ? null : cls(c.getParent()))
                found = c.getMethod(ref.getDescriptor());
            if (found == null) throw new IllegalStateException("PlayerProbe: game method missing: " + ref);
            if (found.hasModifier(ElementModifier.STATIC) != isStatic)
                throw new IllegalStateException("PlayerProbe: static mismatch: " + ref);
            return ref;
        }
        void field(String owner, String name, boolean isStatic) {
            org.teavm.model.FieldReader found = null;
            for (var c = cls(owner); c != null && found == null; c = c.getParent() == null ? null : cls(c.getParent()))
                found = c.getField(name);
            if (found == null) throw new IllegalStateException("PlayerProbe: game field missing: " + owner + "." + name);
            if (found.hasModifier(ElementModifier.STATIC) != isStatic)
                throw new IllegalStateException("PlayerProbe: static mismatch: " + owner + "." + name);
        }
        /** Static call; signature = params..., result. */
        ValueEmitter stat(String owner, String name, ValueType[] sig, ValueEmitter... args) {
            return pe.invoke(ref(owner, name, true, sig), args);
        }
        /** Virtual call on arg(0) (or {@code self}) cast to {@code owner}. */
        ValueEmitter virt(ValueEmitter self, String owner, String name, ValueType[] sig, ValueEmitter... args) {
            return self.cast(t(owner)).invokeVirtual(ref(owner, name, false, sig), args);
        }
        ValueEmitter virt0(String owner, String name, ValueType result) {
            return virt(arg(0), owner, name, new ValueType[]{result});
        }
        ValueEmitter sget(String owner, String name, ValueType type) { field(owner, name, true); return pe.getField(owner, name, type); }
        ValueEmitter fget(ValueEmitter self, String owner, String name, ValueType type) {
            field(owner, name, false); return self.cast(t(owner)).getField(name, type);
        }
    }

    private static final Map<String, Body> BODIES = new HashMap<>();
    private static void body(String name, Body body) { if (BODIES.put(name, body) != null) throw new IllegalStateException(name); }
    private static void p(String stub, String getter, ValueType result) { body(stub, e -> e.virt0(PLAYER, getter, result)); }
    static {
        body("gIngameInstance", e -> e.sget(INGAME, "instance", t(INGAME)));
        body("gIngameTicks", e -> e.fget(e.arg(0), INGAME, "numberTicks", J));
        body("gIngamePaused", e -> e.fget(e.arg(0), INGAME, "paused", Z));
        body("gIsGamePaused", e -> e.stat("zombie.GameTime", "isGamePaused", new ValueType[]{Z}));
        body("gGameTimeInstance", e -> e.stat("zombie.GameTime", "getInstance", new ValueType[]{t("zombie.GameTime")}));
        body("gMultiplier", e -> e.virt0("zombie.GameTime", "getMultiplier", F));
        body("gTrueMultiplier", e -> e.virt0("zombie.GameTime", "getTrueMultiplier", F));
        body("gSpeedControls", e -> e.stat("zombie.ui.UIManager", "getSpeedControls", new ValueType[]{t("zombie.ui.SpeedControls")}));
        body("gCurrentGameSpeed", e -> e.virt0("zombie.ui.SpeedControls", "getCurrentGameSpeed", I));
        body("gShowPausedMessage", e -> e.stat("zombie.ui.UIManager", "isShowPausedMessage", new ValueType[]{Z}));
        body("gCoreInstance", e -> e.stat("zombie.core.Core", "getInstance", new ValueType[]{t("zombie.core.Core")}));
        body("gDoingTextEntry", e -> e.virt0("zombie.core.Core", "isDoingTextEntry", Z));
        body("gKeyForward", e -> e.sget(KEYBIND, "FORWARD", t(KEYBIND)));
        body("gKeyRight", e -> e.sget(KEYBIND, "RIGHT", t(KEYBIND)));
        body("gKeybindKey", e -> e.virt(e.arg(0), "zombie.core.Core", "getKey", new ValueType[]{t(KEYBIND), I}, e.arg(1).cast(t(KEYBIND))));
        body("gKeybindDown", e -> e.stat("zombie.input.GameKeyboard", "isKeyDown", new ValueType[]{t(KEYBIND), Z}, e.arg(0).cast(t(KEYBIND))));
        body("gKeybindId", e -> e.virt0(KEYBIND, "getId", STR));
        body("gGameKeyboardDown", e -> e.stat("zombie.input.GameKeyboard", "isKeyDown", new ValueType[]{I, Z}, e.arg(0)));
        body("gLwjglKeyboardDown", e -> e.stat("org.lwjglx.input.Keyboard", "isKeyDown", new ValueType[]{I, Z}, e.arg(0)));
        body("gNumPlayers", e -> e.sget(PLAYER, "numPlayers", I));
        body("gPlayers", e -> e.sget(PLAYER, "players", ValueType.arrayOf(t(PLAYER))));
        p("gX", "getX", F); p("gY", "getY", F); p("gZ", "getZ", F);
        p("gPlayerNum", "getPlayerNum", I);
        p("gLocalPlayer", "isLocalPlayer", Z);
        p("gNpc", "isNpc", Z);
        p("gForwardX", "getForwardDirectionX", F); p("gForwardY", "getForwardDirectionY", F);
        p("gMoveForwardVec", "getMoveForwardVec", t(VEC2));
        body("gVecX", e -> e.virt0(VEC2, "getX", F));
        body("gVecY", e -> e.virt0(VEC2, "getY", F));
        p("gIsMoving", "isMoving", Z); p("gIsPlayerMoving", "isPlayerMoving", Z); p("gIsWalking", "isWalking", Z);
        p("gIsRunning", "isRunning", Z); p("gIsSprinting", "isSprinting", Z); p("gIsAiming", "isAiming", Z);
        p("gAutoWalk", "isAutoWalk", Z); p("gBlockMovement", "isBlockMovement", Z);
        p("gPerformingAction", "isPerformingAnAction", Z); p("gPathfindRunning", "isPathfindRunning", Z);
        p("gGhost", "isGhostMode", Z); p("gDead", "isDead", Z); p("gAsleep", "isAsleep", Z);
        p("gFalling", "isbFalling", Z); p("gSitOnGround", "isSitOnGround", Z);
        p("gMoveSpeed", "getMoveSpeed", F); p("gPathSpeed", "getPathSpeed", F);
        p("gCurrentStateName", "getCurrentStateName", STR);
        p("gActionStateName", "getActionStateName", STR);
        p("gActionContextStateName", "getCurrentActionContextStateName", STR);
        p("gHasAnimationPlayer", "hasAnimationPlayer", Z);
        p("gAnimUpdating", "isAnimationUpdatingThisFrame", Z);
        // getDeferredMovement(Vector2) = getDeferredMovement(v, false): copia sin poner a cero el acumulador.
        body("gDeferredMovement", e -> e.virt(e.arg(0), PLAYER, "getDeferredMovement",
            new ValueType[]{t(VEC2), t(VEC2)}, e.pe.construct(VEC2)));
        p("gAdvancedAnimator", "getAdvancedAnimator", t(ANIMATOR));
        body("gAnimatorStateName", e -> e.virt0(ANIMATOR, "getCurrentStateName", STR));
        body("gRootLayer", e -> e.virt0(ANIMATOR, "getRootLayer", t(LAYER)));
        body("gSubLayerCount", e -> e.virt0(ANIMATOR, "getSubLayerCount", I));
        body("gSubLayerAt", e -> e.virt(e.arg(0), ANIMATOR, "getSubLayerAt", new ValueType[]{I, t(LAYER)}, e.arg(1)));
        body("gLayerStateName", e -> e.virt0(LAYER, "getCurrentStateName", STR));
        body("gLayerDebugNodeName", e -> e.virt0(LAYER, "getDebugNodeName", STR));
        body("gLiveAnimNodes", e -> e.virt0(LAYER, "getLiveAnimNodes", t("java.util.List")));
        body("gNodeName", e -> e.virt0(LIVE_NODE, "getName", STR));
        body("gNodeWeight", e -> e.virt0(LIVE_NODE, "getWeight", F));
        body("gNodeActive", e -> e.virt0(LIVE_NODE, "isActive", Z));

        // Frente P: pistas del AnimationPlayer, reloj y huesos (getters/campos públicos sin efectos, javap -c).
        String gt = "zombie.GameTime";
        body("gTimeDelta", e -> e.virt0(gt, "getTimeDelta", F));
        body("gFpsMultiplier", e -> e.fget(e.arg(0), gt, "fpsMultiplier", F));
        body("gPerObjectMultiplier", e -> e.fget(e.arg(0), gt, "perObjectMultiplier", F));
        body("gMultiplierBias", e -> e.fget(e.arg(0), gt, "multiplierBias", F));
        body("gSlomo", e -> e.stat(gt, "getSlomoMultiplier", new ValueType[]{F}));
        body("gAverageFps", e -> e.sget("zombie.GameWindow", "averageFPS", F));
        p("gAnimTimeDelta", "getAnimationTimeDelta", F);
        // getModelInstance() sólo lee legsSprite.modelSlot.model; getAnimationPlayer() puede alocar: no se usa.
        p("gModelInstance", "getModelInstance", t(MODEL_INSTANCE));
        body("gInstanceAnimPlayer", e -> e.fget(e.arg(0), MODEL_INSTANCE, "animPlayer", t(ANIM_PLAYER)));
        body("gApAngle", e -> e.virt0(ANIM_PLAYER, "getAngle", F));
        body("gBoneTransformsTimeDelta", e -> e.virt0(ANIM_PLAYER, "getBoneTransformsTimeDelta", F));
        body("gMultiTrack", e -> e.virt0(ANIM_PLAYER, "getMultiTrack", t(MULTI_TRACK)));
        body("gTrackCount", e -> e.virt0(MULTI_TRACK, "getTrackCount", I));
        body("gTrackAt", e -> e.virt(e.arg(0), MULTI_TRACK, "getTrackAt", new ValueType[]{I, t(TRACK)}, e.arg(1)));
        body("gTrackName", e -> e.virt0(TRACK, "getName", STR));
        body("gTrackCur", e -> e.virt0(TRACK, "getCurrentTimeValue", F));
        body("gTrackPrev", e -> e.virt0(TRACK, "getPreviousTimeValue", F));
        body("gTrackDur", e -> e.virt0(TRACK, "getDuration", F));
        body("gTrackAcc", e -> e.virt0(TRACK, "getAccumulatedTimeValue", F));
        body("gTrackPlaying", e -> e.fget(e.arg(0), TRACK, "isPlaying", Z));
        body("gTrackLooping", e -> e.virt0(TRACK, "isLooping", Z));
        body("gTrackReverse", e -> e.fget(e.arg(0), TRACK, "reverse", Z));
        body("gTrackFinished", e -> e.virt0(TRACK, "isFinished", Z));
        body("gTrackSpeed", e -> e.virt0(TRACK, "getSpeedDelta", F));
        body("gTrackWeight", e -> e.virt0(TRACK, "getBlendWeight", F));
        body("gTrackLayer", e -> e.virt0(TRACK, "getLayerIdx", I));
        body("gTrackDeferredBone", e -> e.virt0(TRACK, "getDeferredMovementBoneIdx", I));
        body("gTrackUseDeferred", e -> e.virt0(TRACK, "getUseDeferredMovement", Z));
        body("gTrackClip", e -> e.virt0(TRACK, "getClip", t(CLIP)));
        body("gClipDuration", e -> e.virt0(CLIP, "getDuration", F));
        body("gClipKeyframes", e -> e.virt0(CLIP, "getKeyframes", ValueType.arrayOf(t(KEYFRAME))));
        body("gClipBoneFrames", e -> e.virt(e.arg(0), CLIP, "getBoneFramesAt", new ValueType[]{I, ValueType.arrayOf(t(KEYFRAME))}, e.arg(1)));
        body("gKeyTime", e -> e.fget(e.arg(0), KEYFRAME, "time", F));
        body("gKeyRot", e -> e.fget(e.arg(0), KEYFRAME, "rotation", t(QUAT)));
        body("gKeyPos", e -> e.fget(e.arg(0), KEYFRAME, "position", t(VEC3)));
        body("gQx", e -> e.fget(e.arg(0), QUAT, "x", F));
        body("gQy", e -> e.fget(e.arg(0), QUAT, "y", F));
        body("gQz", e -> e.fget(e.arg(0), QUAT, "z", F));
        body("gQw", e -> e.fget(e.arg(0), QUAT, "w", F));
        body("gV3x", e -> e.fget(e.arg(0), VEC3, "x", F));
        body("gV3y", e -> e.fget(e.arg(0), VEC3, "y", F));
        body("gV3z", e -> e.fget(e.arg(0), VEC3, "z", F));
        body("gModelTransformsCount", e -> e.virt0(ANIM_PLAYER, "getModelTransformsCount", I));
        body("gModelTransformAt", e -> e.virt(e.arg(0), ANIM_PLAYER, "getModelTransformAt", new ValueType[]{I, t(MATRIX)}, e.arg(1)));
        body("gBoneIndex", e -> e.virt(e.arg(0), ANIM_PLAYER, "getSkinningBoneIndex", new ValueType[]{STR, I, I}, e.arg(1), e.arg(2)));
        body("gNewMatrix", e -> e.pe.construct(MATRIX));
        // getBoneTransform(i, m) = boneTransforms[i].getMatrix(m): copia la matriz local (validación perezosa PRS->matriz).
        body("gBoneLocal", e -> e.virt(e.arg(0), ANIM_PLAYER, "getBoneTransform", new ValueType[]{I, t(MATRIX), t(MATRIX)},
            e.arg(1), e.arg(2).cast(t(MATRIX))));
        body("gMatrixStore", e -> e.virt(e.arg(0), MATRIX, "store", new ValueType[]{t("java.nio.FloatBuffer"), t("org.lwjgl.util.vector.Matrix")},
            e.arg(1).cast(t("java.nio.FloatBuffer"))));
        String sd = "zombie.core.skinnedmodel.model.SkinningData";
        body("gSkinningData", e -> e.virt0(ANIM_PLAYER, "getSkinningData", t(sd)));
        body("gSkeletonHierarchy", e -> e.fget(e.arg(0), sd, "skeletonHierarchy", t("java.util.List")));
        body("gBindPose", e -> e.fget(e.arg(0), sd, "bindPose", t("java.util.List")));
        body("gBoneOffset", e -> e.fget(e.arg(0), sd, "boneOffset", t("java.util.List")));
    }

    /** Nombres de stub con reescritura (PlayerProbeTest los compara con PlayerProbe). */
    public static java.util.Set<String> rewrittenStubs() { return java.util.Set.copyOf(BODIES.keySet()); }

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals(INGAME)) injectFrame(cls);
        else if (cls.getName().equals(PROBE)) rewriteStubs(cls, context);
    }

    private static void injectFrame(ClassHolder cls) {
        MethodHolder update = cls.getMethod(new MethodDescriptor("update",
            ValueType.object("zombie.gameStates.GameStateMachine$StateAction")));
        if (update == null || update.getProgram() == null)
            throw new IllegalStateException("PlayerProbe: IngameState#update()StateAction missing");
        var program = update.getProgram();
        int exits = 0;
        for (var block : program.getBasicBlocks()) {
            for (Instruction i = block.getFirstInstruction(); i != null; i = i.getNext()) {
                if (!(i instanceof ExitInstruction)) continue;
                var call = new InvokeInstruction();
                call.setType(InvocationType.SPECIAL);
                call.setMethod(new MethodReference(PROBE, "frame", ValueType.VOID));
                i.insertPrevious(call);
                exits++;
            }
        }
        if (exits == 0) throw new IllegalStateException("PlayerProbe: IngameState#update() has no return");
    }

    private static void rewriteStubs(ClassHolder cls, ClassHolderTransformerContext context) {
        int done = 0;
        for (MethodHolder method : cls.getMethods()) {
            String name = method.getName();
            if (!method.hasModifier(ElementModifier.STATIC) || !name.startsWith("g") || name.length() < 2
                    || !Character.isUpperCase(name.charAt(1))) continue;
            Body body = BODIES.get(name);
            if (body == null) throw new IllegalStateException("PlayerProbe." + name + " has no TeaVM rewrite");
            var e = new E(ProgramEmitter.create(method, context.getHierarchy()), method, context);
            ValueEmitter value = body.emit(e);
            ValueType result = method.getResultType();
            if (result instanceof ValueType.Object || result instanceof ValueType.Array) {
                if (!value.getType().equals(result)) value = value.cast(result);
            }
            value.returnValue();
            done++;
        }
        if (done != BODIES.size()) throw new IllegalStateException("PlayerProbe: rewrote " + done + " of " + BODIES.size());
    }
}
