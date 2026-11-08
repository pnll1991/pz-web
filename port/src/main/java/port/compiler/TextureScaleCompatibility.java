package port.compiler;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * Ronda 9X: activa los tiles 1x cuando el navegador pide ?tile1x=1 (ver port.runtime.TextureScale).
 *
 * 1. Inyecta {@code port.runtime.TextureScale.apply()} como primera instrucción de
 *    {@code zombie.GameWindow#enter()} (privada y estática; {@code javap -c} sobre el jar fijado muestra que
 *    lee {@code Core.tileScale} para elegir los packs de textura antes de montar ninguno).
 * 2. Reescribe el stub {@code apply()} con {@code Core.tileScale = TextureScale.tile1x()} (1 con el
 *    flag, 2 sin él; el 2 es el valor que el {@code <clinit>} del juego ya pone). El método llamado es un
 *    reenvío Java común al {@code @JSBody}, el patrón del repo para JS inyectado (ver StartupProgress.sideEffect). El build falla si falta el método o el campo, en
 *    lugar de compilar un modo que mienta (misma política que PlayerProbeCompatibility).
 */
public final class TextureScaleCompatibility implements ClassHolderTransformer {
    private static final String GAME_WINDOW = "zombie.GameWindow";
    private static final String SCALE = "port.runtime.TextureScale";
    private static final String CORE = "zombie.core.Core";

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals(GAME_WINDOW)) injectApply(cls);
        else if (cls.getName().equals(SCALE)) rewriteApply(cls, context);
    }

    private static void injectApply(ClassHolder cls) {
        MethodHolder enter = cls.getMethod(new MethodDescriptor("enter", ValueType.VOID));
        if (enter == null || enter.getProgram() == null)
            throw new IllegalStateException("TextureScale: GameWindow#enter() missing");
        var entry = enter.getProgram().basicBlockAt(0);
        if (entry == null) throw new IllegalStateException("TextureScale: GameWindow#enter() has no blocks");
        var call = new InvokeInstruction();
        call.setType(InvocationType.SPECIAL);
        call.setMethod(new MethodReference(SCALE, "apply", ValueType.VOID));
        var first = entry.getFirstInstruction();
        if (first != null) first.insertPrevious(call);
        else entry.add(call);
    }

    private static void rewriteApply(ClassHolder cls, ClassHolderTransformerContext context) {
        MethodHolder apply = cls.getMethod(new MethodDescriptor("apply", ValueType.VOID));
        if (apply == null || apply.getProgram() == null)
            throw new IllegalStateException("TextureScale.apply() missing");
        var core = context.getHierarchy().getClassSource().get(CORE);
        if (core == null) throw new IllegalStateException("TextureScale: game class missing: " + CORE);
        var field = core.getField("tileScale");
        if (field == null || !field.hasModifier(ElementModifier.STATIC)
                || !ValueType.INTEGER.equals(field.getType()))
            throw new IllegalStateException("TextureScale: zombie.core.Core.tileScale (static int) missing");
        var pe = ProgramEmitter.create(apply, context.getHierarchy());
        var scale = pe.invoke(new MethodReference(SCALE, "tile1x", ValueType.INTEGER));
        pe.setField(CORE, "tileScale", scale);
        pe.exit();
    }
}
