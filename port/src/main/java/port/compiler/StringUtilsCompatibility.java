package port.compiler;
import org.teavm.model.*;
import org.teavm.model.emit.*;

/**
 * Rewrites only the two regex helpers of {@code zombie.util.StringUtils}.
 * Verified cause (round 49, diag144/diag146): `containsWhitespace` is
 * {@code String.matches("(.*?)\\s(.*?)")} and TeaVM's {@code ReluctantGroupQuantifierSet::matches}
 * recurses per character expansion, so a long recipe line from {@code CraftRecipe::LoadIO} produced
 * {@code RangeError: Maximum call stack size exceeded} (5504 JS frames) inside
 * ScriptManager::Load -> GameWindow::initShared. {@code removeWhitespace} uses the same reluctant
 * machinery via {@code replaceAll("\\s","")}. Both are replaced by the loops in
 * {@code port.runtime.text.StringUtilsBridge}, which keep the desktop semantics ([ \t\n\x0B\f\r]);
 * every other method of the class keeps its original bytecode (only these two are replaced).
 */
public final class StringUtilsCompatibility implements ClassHolderTransformer {
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals("zombie.util.StringUtils")) return;
        var own = context.getHierarchy().getClassSource().get("port.runtime.text.StringUtilsBridge");
        if (own == null) throw new IllegalStateException("StringUtils bridge missing");
        var replacements = java.util.Map.of(
            // TeaVM's MethodDescriptor.parse takes "name(args)ret" with NO colon (verified:
            // parse("containsWhitespace:(...)Z") yields a descriptor that is not equals() to
            // MethodHolder.getDescriptor(), so the round-49 version matched nothing and boot.wasm
            // still called java.lang.String::matches).
            "containsWhitespace", MethodDescriptor.parse("containsWhitespace(Ljava/lang/String;)Z"),
            "removeWhitespace", MethodDescriptor.parse("removeWhitespace(Ljava/lang/String;)Ljava/lang/String;"));
        for (var method : cls.getMethods()) {
            if (!method.hasModifier(ElementModifier.STATIC)) continue;
            var wanted = replacements.get(method.getName());
            if (wanted == null || !wanted.equals(method.getDescriptor())) continue;
            var equivalent = own.getMethod(wanted);
            if (equivalent == null) throw new IllegalStateException(
                "StringUtils bridge method missing: " + method.getName() + wanted.signatureToString());
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            ValueEmitter[] args = new ValueEmitter[method.parameterCount()];
            for (int i = 0; i < args.length; i++) args[i] = emitter.var(i + 1, method.parameterType(i));
            var result = emitter.invoke(new MethodReference(own.getName(), method.getDescriptor()), args);
            if (method.getResultType() == ValueType.VOID) emitter.exit(); else result.returnValue();
        }
    }
}
