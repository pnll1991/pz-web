package org.teavm.model.util;

import org.teavm.model.PrimitiveType;

public final class ReflectionUtil {
   private ReflectionUtil() {
   }

   public static String typeName(PrimitiveType type) {
      switch (type) {
         case BOOLEAN:
            return "boolean";
         case BYTE:
            return "byte";
         case SHORT:
            return "short";
         case CHARACTER:
            return "char";
         case INTEGER:
            return "int";
         case LONG:
            return "long";
         case FLOAT:
            return "float";
         case DOUBLE:
            return "double";
         default:
            throw new IllegalArgumentException();
      }
   }
}
