package org.teavm.model;

import java.util.stream.Stream;

public interface ClassHolderSource extends ClassReaderSource {
   ClassHolder get(String var1);

   default Stream<ClassHolder> getMutableAncestors(String name) {
      return this.getAncestors(name).map(cls -> (ClassHolder)cls);
   }

   default Stream<ClassHolder> getMutableAncestorClasses(String name) {
      return this.getAncestorClasses(name).map(cls -> (ClassHolder)cls);
   }

   default MethodHolder resolveMutable(MethodReference method) {
      return (MethodHolder)this.resolve(method);
   }

   default MethodHolder resolveMutableImplementation(MethodReference method) {
      return (MethodHolder)this.resolveImplementation(method);
   }

   default MethodHolder resolveMutableImplementation(String className, MethodDescriptor descriptor) {
      return (MethodHolder)this.resolveImplementation(className, descriptor);
   }

   default FieldHolder resolveMutable(FieldReference field) {
      return (FieldHolder)this.resolve(field);
   }

   default Stream<MethodHolder> mutableOverriddenMethods(MethodReference method) {
      return this.overriddenMethods(method).map(m -> (MethodHolder)m);
   }
}
