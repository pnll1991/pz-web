package port.runtime;

import java.util.Arrays;

/**
 * Compile-time caller stack for {@code zombie.ai.State$Param} factories.
 *
 * On the JVM {@code State$Param.<init>} discovers its caller with
 * {@code StackWalker.getInstance(RETAIN_CLASS_REFERENCE).walk(...)}, filtering out its own class, so the
 * result is always the class that invoked a {@code Param.ofX(...)} factory. TeaVM's Wasm GC
 * {@code TThread.getStackTrace()} returns an empty array, so that walk yields {@code null} and
 * {@code State.class.isAssignableFrom(null)} traps with "dereferencing a null pointer" -- an
 * uncatchable Wasm trap that kills the MainThread (measured in {@code diag135.log}, via
 * {@code AttackState.<clinit>}).
 *
 * Which class calls a factory is known at compile time, so {@code StateParamCallerCompatibility} wraps
 * every such call site with {@link #enter(Class)} / {@link #exit()}; {@link #callerClass()} then answers
 * with exactly what the JVM walk would have produced. A factory reached without an instrumented call
 * site is a build-shape mismatch and fails loudly here instead of reporting a fabricated caller.
 */
public final class StateParamCaller {
    private static Class<?>[] stack = new Class<?>[16];
    private static int depth;

    private StateParamCaller() { }

    public static void enter(Class<?> caller) {
        if (depth == stack.length) stack = Arrays.copyOf(stack, stack.length * 2);
        stack[depth++] = caller;
    }

    public static void exit() {
        if (depth == 0) throw new IllegalStateException("State$Param: exit() without a matching enter()");
        stack[--depth] = null;
    }

    /** Same contract as the original walk: the class that called the factory. */
    public static Object callerClass() {
        if (depth == 0) throw new IllegalStateException(
            "State$Param: factory called without an instrumented call site (StateParamCallerCompatibility "
                + "did not wrap this invocation)");
        return stack[depth - 1];
    }

}
