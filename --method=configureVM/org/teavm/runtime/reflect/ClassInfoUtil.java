package org.teavm.runtime.reflect;

public final class ClassInfoUtil {
   private ClassInfoUtil() {
   }

   public static ClassInfo resolve(DerivedClassInfo info) {
      ClassInfo cls = info.classInfo();

      for (int i = 0; i < info.arrayDegree(); i++) {
         cls = cls.arrayType();
      }

      return cls;
   }
}
