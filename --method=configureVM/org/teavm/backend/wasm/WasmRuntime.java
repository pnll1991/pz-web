package org.teavm.backend.wasm;

import org.teavm.interop.Intrinsified;

public final class WasmRuntime {
   private WasmRuntime() {
   }

   @Intrinsified
   public static int compare(int a, int b) {
      return gt(a, b) ? 1 : (lt(a, b) ? -1 : 0);
   }

   @Intrinsified
   public static int compareUnsigned(int a, int b) {
      return gtu(a, b) ? 1 : (ltu(a, b) ? -1 : 0);
   }

   @Intrinsified
   public static int compareUnsigned(long a, long b) {
      return gtu(a, b) ? 1 : (ltu(a, b) ? -1 : 0);
   }

   @Intrinsified
   public static int compare(long a, long b) {
      return gt(a, b) ? 1 : (lt(a, b) ? -1 : 0);
   }

   @Intrinsified
   public static int compare(float a, float b) {
      return a == b ? 0 : (lt(a, b) ? -1 : 1);
   }

   @Intrinsified
   public static int compareLess(float a, float b) {
      return a == b ? 0 : (gt(a, b) ? 1 : -1);
   }

   @Intrinsified
   public static int compare(double a, double b) {
      return a == b ? 0 : (lt(a, b) ? -1 : 1);
   }

   @Intrinsified
   public static int compareLess(double a, double b) {
      return a == b ? 0 : (gt(a, b) ? 1 : -1);
   }

   @Intrinsified
   public static native float min(float var0, float var1);

   @Intrinsified
   public static native double min(double var0, double var2);

   @Intrinsified
   public static native float max(float var0, float var1);

   @Intrinsified
   public static native double max(double var0, double var2);

   @Intrinsified
   public static float remainder(float a, float b) {
      return a - (int)(a / b) * b;
   }

   @Intrinsified
   public static double remainder(double a, double b) {
      return a - (long)(a / b) * b;
   }

   @Intrinsified
   private static native boolean lt(int var0, int var1);

   @Intrinsified
   private static native boolean gt(int var0, int var1);

   @Intrinsified
   private static native boolean ltu(int var0, int var1);

   @Intrinsified
   private static native boolean gtu(int var0, int var1);

   @Intrinsified
   private static native boolean lt(long var0, long var2);

   @Intrinsified
   private static native boolean gt(long var0, long var2);

   @Intrinsified
   private static native boolean ltu(long var0, long var2);

   @Intrinsified
   private static native boolean gtu(long var0, long var2);

   @Intrinsified
   private static native boolean lt(float var0, float var1);

   @Intrinsified
   private static native boolean gt(float var0, float var1);

   @Intrinsified
   private static native boolean lt(double var0, double var2);

   @Intrinsified
   private static native boolean gt(double var0, double var2);
}
