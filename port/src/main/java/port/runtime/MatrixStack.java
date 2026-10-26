package port.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/** Column-major OpenGL matrix operations, independent of any game classes. */
public final class MatrixStack {
    private float[] current = identity();
    private final Deque<float[]> stack = new ArrayDeque<>();
    private static float[] identity() {
        float[] matrix = new float[16];
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1;
        return matrix;
    }
    public void loadIdentity() { current = identity(); }
    public float[] copy() { return current.clone(); }
    public void push() {
        if (stack.size() >= 32) throw new IllegalStateException("Matrix stack overflow");
        stack.push(current.clone());
    }
    public void pop() {
        if (stack.isEmpty()) throw new IllegalStateException("Matrix stack underflow");
        current = stack.pop();
    }
    public void multiply(float[] right) {
        if (right.length != 16) throw new IllegalArgumentException("Matrix must have 16 elements");
        float[] result = new float[16];
        for (int column = 0; column < 4; column++)
            for (int row = 0; row < 4; row++)
                for (int k = 0; k < 4; k++)
                    result[column * 4 + row] += current[k * 4 + row] * right[column * 4 + k];
        current = result;
    }
    public void translate(float x, float y, float z) {
        float[] matrix = identity();
        matrix[12] = x; matrix[13] = y; matrix[14] = z;
        multiply(matrix);
    }
    public static int[] triangulateQuads(int vertices) {
        if (vertices < 0 || vertices % 4 != 0) throw new IllegalArgumentException("GL_QUADS needs groups of four vertices");
        int[] indices = new int[vertices / 4 * 6];
        int[] offsets = {0, 1, 2, 0, 2, 3};
        for (int q = 0; q < vertices / 4; q++)
            for (int i = 0; i < 6; i++) indices[q * 6 + i] = q * 4 + offsets[i];
        return indices;
    }
}
