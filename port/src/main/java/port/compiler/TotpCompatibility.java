package port.compiler;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 * Round 82: de.taimos.totp.TOTP.hmac_sha1([B[B)[B uses javax.crypto.Mac, which TeaVM does not provide, and the
 * multiplayer login packets make it reachable (compile error "Class javax.crypto.Mac was not found"). Only that
 * method is replaced, by port.runtime.text.HmacSha1Bridge (real HMAC-SHA1); the rest of TOTP keeps its bytecode.
 */
public final class TotpCompatibility implements ClassHolderTransformer {
    static final String TARGET = "de.taimos.totp.TOTP", BRIDGE = "port.runtime.text.HmacSha1Bridge";
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(TARGET)) return;
        MethodDescriptor wanted = MethodDescriptor.parse("hmac_sha1([B[B)[B");
        int patched = 0;
        for (MethodHolder method : cls.getMethods()) {
            if (!method.hasModifier(ElementModifier.STATIC) || !wanted.equals(method.getDescriptor())) continue;
            var emitter = ProgramEmitter.create(method, context.getHierarchy());
            ValueEmitter[] args = new ValueEmitter[method.parameterCount()];
            for (int i = 0; i < args.length; i++) args[i] = emitter.var(i + 1, method.parameterType(i));
            emitter.invoke(new MethodReference(BRIDGE, method.getDescriptor()), args).returnValue();
            patched++;
        }
        if (patched != 1) throw new IllegalStateException("TOTP.hmac_sha1 not found: the jar changed, refusing to guess");
    }
}
