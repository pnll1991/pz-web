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
        var child = new ReflectionProbe.Child();
        if (!"reflection-child-ok".equals(ReflectionProbe.class.getMethod("value").invoke(child)))
            throw new AssertionError("Reflective virtual dispatch");
        var label = ReflectionProbe.class.getField("label");
        label.set(child, "updated");
        if (!"updated".equals(label.get(child))) throw new AssertionError("Reflective object field");
        var number = ReflectionProbe.class.getField("number");
        number.setInt(child, 7429);
        if (number.getInt(child) != 7429) throw new AssertionError("Reflective primitive field");
    }
}
