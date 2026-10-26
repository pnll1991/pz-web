package port.runtime;

import java.math.BigInteger;

/** Exact integer significands followed by one ties-to-even rounding (IEEE 754). */
public final class FusedMath {
    public static float fma(float a, float b, float c) {
        if (!Float.isFinite(a) || !Float.isFinite(b)) return a * b + c;
        if (!Float.isFinite(c)) return c;
        return Float.intBitsToFloat((int) fused(Float.floatToRawIntBits(a) & 0xffffffffL,
                Float.floatToRawIntBits(b) & 0xffffffffL, Float.floatToRawIntBits(c) & 0xffffffffL, 23, 8));
    }
    public static double fma(double a, double b, double c) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) return a * b + c;
        if (!Double.isFinite(c)) return c;
        return Double.longBitsToDouble(fused(Double.doubleToRawLongBits(a), Double.doubleToRawLongBits(b),
                Double.doubleToRawLongBits(c), 52, 11));
    }
    private record Number(BigInteger mantissa, int scale, boolean negative) { }
    private static Number number(long bits, int fraction, int exponent) {
        int bias = (1 << (exponent - 1)) - 1;
        int exp = (int) (bits >>> fraction) & ((1 << exponent) - 1);
        long mantissa = bits & ((1L << fraction) - 1);
        if (exp != 0) mantissa |= 1L << fraction;
        return new Number(BigInteger.valueOf(mantissa), (exp == 0 ? 1 : exp) - bias - fraction,
                (bits & (1L << (fraction + exponent))) != 0);
    }
    private static long fused(long aBits, long bBits, long cBits, int fraction, int exponent) {
        var a = number(aBits, fraction, exponent); var b = number(bBits, fraction, exponent); var c = number(cBits, fraction, exponent);
        int productScale = a.scale + b.scale, scale = Math.min(productScale, c.scale);
        boolean productNegative = a.negative ^ b.negative;
        var product = a.mantissa.multiply(b.mantissa).shiftLeft(productScale - scale);
        var addend = c.mantissa.shiftLeft(c.scale - scale);
        var sum = (productNegative ? product.negate() : product).add(c.negative ? addend.negate() : addend);
        long sign = 1L << (fraction + exponent);
        if (sum.signum() == 0) return productNegative && c.negative ? sign : 0;
        if (sum.signum() > 0) sign = 0;
        sum = sum.abs();
        int bias = (1 << (exponent - 1)) - 1, minimum = 1 - bias;
        int targetScale = Math.max(sum.bitLength() - 1 + scale - fraction, minimum - fraction);
        int shift = targetScale - scale;
        BigInteger rounded;
        if (shift <= 0) rounded = sum.shiftLeft(-shift);
        else {
            rounded = sum.shiftRight(shift);
            var remainder = sum.subtract(rounded.shiftLeft(shift));
            int comparison = remainder.compareTo(BigInteger.ONE.shiftLeft(shift - 1));
            if (comparison > 0 || comparison == 0 && rounded.testBit(0)) rounded = rounded.add(BigInteger.ONE);
        }
        if (rounded.signum() == 0) return sign;
        int resultExponent = rounded.bitLength() - 1 + targetScale;
        if (resultExponent > bias) return sign | ((1L << exponent) - 1) << fraction;
        if (resultExponent < minimum) return sign | rounded.longValue();
        long mantissa = rounded.shiftRight(rounded.bitLength() - 1 - fraction).longValue();
        return sign | (long) (resultExponent + bias) << fraction | mantissa & ((1L << fraction) - 1);
    }
}
