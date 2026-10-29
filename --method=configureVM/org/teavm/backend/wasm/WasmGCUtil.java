package org.teavm.backend.wasm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;

public final class WasmGCUtil {
   private WasmGCUtil() {
   }

   public static String findCommonSuperclass(ClassHierarchy hierarchy, ClassReader a, ClassReader b) {
      List<ClassReader> firstPath = findPathToRoot(hierarchy, a);
      Collections.reverse(firstPath);
      List<ClassReader> secondPath = findPathToRoot(hierarchy, b);
      Collections.reverse(secondPath);
      if (firstPath.get(0) != secondPath.get(0)) {
         return "java.lang.Object";
      }

      int min = Math.min(firstPath.size(), secondPath.size()) - 1;
      int index = 0;

      while (index < min && firstPath.get(index + 1) == secondPath.get(index + 1)) {
         index++;
      }

      return index < firstPath.size() ? firstPath.get(index).getName() : secondPath.get(index).getName();
   }

   private static List<ClassReader> findPathToRoot(ClassHierarchy hierarchy, ClassReader cls) {
      ArrayList<ClassReader> path = new ArrayList<>();

      while (cls != null) {
         path.add(cls);
         cls = cls.getParent() != null ? hierarchy.getClassSource().get(cls.getParent()) : null;
      }

      return path;
   }
}
