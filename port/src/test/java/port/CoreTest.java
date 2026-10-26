package port;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import port.runtime.CoreChecks;
import port.runtime.MatrixStack;

public class CoreTest {
    @Test void sameCoreChecksAsBrowser() throws Exception { CoreChecks.run(); }
    @Test void invalidMatrixOperationsFail() {
        assertThrows(IllegalStateException.class, () -> new MatrixStack().pop());
        assertThrows(IllegalArgumentException.class, () -> MatrixStack.triangulateQuads(5));
    }
    @Test void matrixMultiplicationOrder() {
        MatrixStack matrix = new MatrixStack();
        matrix.multiply(new float[]{2,0,0,0,0,3,0,0,0,0,4,0,0,0,0,1});
        matrix.translate(5,6,7);
        assertEquals(10, matrix.copy()[12]);
        assertEquals(18, matrix.copy()[13]);
        assertEquals(28, matrix.copy()[14]);
    }
}
