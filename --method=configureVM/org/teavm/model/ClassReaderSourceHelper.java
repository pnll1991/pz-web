package org.teavm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ClassReaderSourceHelper {
   private ClassReaderSourceHelper() {
   }

   static MethodReader resolveMethodImplementation(ClassReaderSource classSource, String className, MethodDescriptor methodDescriptor, Set<String> visited) {
      if (!visited.add(className)) {
         return null;
      }

      ClassReader cls = classSource.get(className);
      if (cls == null) {
         return null;
      }

      MethodReader method = cls.getMethod(methodDescriptor);
      if (method != null && !method.hasModifier(ElementModifier.ABSTRACT)) {
         return method;
      }

      MethodReader mostSpecificMethod = null;
      List<String> superClasses = new ArrayList<>();
      if (cls.getParent() != null) {
         superClasses.add(cls.getParent());
      }

      superClasses.addAll(cls.getInterfaces());

      for (String superClass : superClasses) {
         MethodReader resultFromSuperClass = resolveMethodImplementation(classSource, superClass, methodDescriptor, visited);
         if (resultFromSuperClass != null
            && (mostSpecificMethod == null || classSource.isSuperType(mostSpecificMethod.getOwnerName(), resultFromSuperClass.getOwnerName()).orElse(false))) {
            mostSpecificMethod = resultFromSuperClass;
         }
      }

      return mostSpecificMethod;
   }

   static Optional<Boolean> isSuperType(ClassReaderSource classSource, String superType, String subType) {
      if (superType.equals("java.lang.Object")) {
         return Optional.of(true);
      }

      ClassReader cls = classSource.get(superType);
      return cls != null && !cls.hasModifier(ElementModifier.INTERFACE)
         ? isSuperTypeSimple(classSource, superType, subType)
         : isSuperTypeInterface(classSource, superType, subType);
   }

   private static Optional<Boolean> isSuperTypeSimple(ClassReaderSource classSource, String superType, String subType) {
      while (!superType.equals(subType)) {
         ClassReader cls = classSource.get(subType);
         if (cls == null) {
            return Optional.empty();
         }

         subType = cls.getParent();
         if (subType == null) {
            return Optional.of(false);
         }
      }

      return Optional.of(true);
   }

   private static Optional<Boolean> isSuperTypeInterface(ClassReaderSource classSource, String superType, String subType) {
      if (superType.equals(subType)) {
         return Optional.of(true);
      }

      ClassReader cls = classSource.get(subType);
      if (cls == null) {
         return Optional.empty();
      }

      if (cls.getParent() != null && isSuperTypeInterface(classSource, superType, cls.getParent()).orElse(false)) {
         return Optional.of(true);
      }

      for (String iface : cls.getInterfaces()) {
         if (isSuperTypeInterface(classSource, superType, iface).orElse(false)) {
            return Optional.of(true);
         }
      }

      return Optional.of(false);
   }
}
