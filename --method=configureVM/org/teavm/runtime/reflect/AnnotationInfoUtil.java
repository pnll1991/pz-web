package org.teavm.runtime.reflect;

import java.lang.annotation.Annotation;

public final class AnnotationInfoUtil {
   private AnnotationInfoUtil() {
   }

   public static Annotation createAnnotation(AnnotationInfo info) {
      return (Annotation)info.constructor().createAnnotation(info.data());
   }

   public static boolean[] asBooleanArray(AnnotationValueArray array) {
      boolean[] result = new boolean[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getBoolean(i);
      }

      return result;
   }

   public static byte[] asByteArray(AnnotationValueArray array) {
      byte[] result = new byte[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getByte(i);
      }

      return result;
   }

   public static short[] asShortArray(AnnotationValueArray array) {
      short[] result = new short[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getShort(i);
      }

      return result;
   }

   public static char[] asCharArray(AnnotationValueArray array) {
      char[] result = new char[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getChar(i);
      }

      return result;
   }

   public static int[] asIntArray(AnnotationValueArray array) {
      int[] result = new int[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getInt(i);
      }

      return result;
   }

   public static long[] asLongArray(AnnotationValueArray array) {
      long[] result = new long[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getLong(i);
      }

      return result;
   }

   public static float[] asFloatArray(AnnotationValueArray array) {
      float[] result = new float[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getFloat(i);
      }

      return result;
   }

   public static double[] asDoubleArray(AnnotationValueArray array) {
      double[] result = new double[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getDouble(i);
      }

      return result;
   }

   public static Class<?>[] asClassArray(AnnotationValueArray array) {
      Class<?>[] result = new Class[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = ClassInfoUtil.resolve(array.getClass(i)).classObject();
      }

      return result;
   }

   public static String[] asStringArray(AnnotationValueArray array) {
      String[] result = new String[array.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = array.getString(i).getStringObject();
      }

      return result;
   }

   public static void fillAnnotationArray(AnnotationValueArray source, AnnotationConstructor constructor, Annotation[] target) {
      for (int i = 0; i < target.length; i++) {
         target[i] = (Annotation)constructor.createAnnotation(source.getAnnotation(i));
      }
   }

   public static void fillEnumArray(AnnotationValueArray source, Enum<?>[] values, Enum<?>[] target) {
      for (int i = 0; i < target.length; i++) {
         target[i] = values[source.getEnum(i)];
      }
   }
}
