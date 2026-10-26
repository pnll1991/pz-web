package port;

import org.junit.jupiter.api.Test;
import java.util.Random;
import port.runtime.FusedMath;
import static org.junit.jupiter.api.Assertions.*;

class FusedMathTest {
    @Test void matchesJdkAtRoundingBoundariesAndSpecialValues() {
        double[] values = {0.0, -0.0, 1, -1, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_NORMAL,
                Double.MAX_VALUE, -Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN, Math.nextUp(1.0)};
        for (double a : values) for (double b : values) for (double c : values) check(a,b,c);
    }
    @Test void randomFloatAndDoubleTriplesMatchJdk() {
        var random = new Random(421);
        for (int i = 0; i < 5000; i++) {
            check(Double.longBitsToDouble(random.nextLong()), Double.longBitsToDouble(random.nextLong()), Double.longBitsToDouble(random.nextLong()));
            float a = Float.intBitsToFloat(random.nextInt()), b = Float.intBitsToFloat(random.nextInt()), c = Float.intBitsToFloat(random.nextInt());
            assertEquals(Float.floatToIntBits(Math.fma(a,b,c)), Float.floatToIntBits(FusedMath.fma(a,b,c)));
        }
    }
    private static void check(double a, double b, double c) {
        assertEquals(Double.doubleToLongBits(Math.fma(a,b,c)), Double.doubleToLongBits(FusedMath.fma(a,b,c)), () -> a + " * " + b + " + " + c);
    }
}
