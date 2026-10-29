package org.teavm.backend.lowlevel.generate;

import org.teavm.model.ClassReader;
import org.teavm.model.FieldReader;
import org.teavm.model.ValueType;

public final class ClassGeneratorUtil {
   private ClassGeneratorUtil() {
   }

   public static int applyPrimitiveFlags(int flags, ValueType type) {
      if (type instanceof ValueType.Primitive) {
         flags |= getPrimitiveFlag((ValueType.Primitive)type) << 4;
      } else {
         flags |= 144;
      }

      return flags;
   }

   private static int getPrimitiveFlag(ValueType.Primitive type) {
      switch (type.getKind()) {
         case BOOLEAN:
            return 1;
         case BYTE:
            return 2;
         case SHORT:
            return 3;
         case CHARACTER:
            return 4;
         case INTEGER:
            return 5;
         case LONG:
            return 6;
         case FLOAT:
            return 7;
         case DOUBLE:
            return 8;
         default:
            throw new AssertionError();
      }
   }

   public static int contributeToFlags(ClassReader cls, int flags) {
      if (cls != null) {
         switch (cls.getName()) {
            case "java.lang.ref.WeakReference":
               flags |= 2;
               break;
            case "java.lang.ref.ReferenceQueue":
               flags |= 4;
         }
      }

      return flags;
   }

   public static boolean isBufferObjectField(FieldReader cls) {
      return cls.getAnnotations().get("java.nio.NativeBufferObjectMarker") != null;
   }
}
