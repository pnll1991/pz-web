package port.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import port.demo.ReflectionProbe;

public final class CoreChecks {
    public static void run() throws ReflectiveOperationException {
        MatrixStack stack = new MatrixStack();
        stack.push(); stack.translate(2, 3, 4);
        if (stack.copy()[12] != 2 || stack.copy()[13] != 3) throw new AssertionError("Translation");
        stack.pop();
        if (stack.copy()[12] != 0) throw new AssertionError("Matrix pop");
        if (!Arrays.equals(MatrixStack.triangulateQuads(8), new int[]{0,1,2,0,2,3,4,5,6,4,6,7}))
            throw new AssertionError("Quad indices");
        Object result = ReflectionProbe.class.getMethod("value").invoke(new ReflectionProbe());
        if (!"reflection-ok".equals(result)) throw new AssertionError("Reflection policy");
    }
}
