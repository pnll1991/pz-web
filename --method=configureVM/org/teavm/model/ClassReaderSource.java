package org.teavm.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface ClassReaderSource {
   ClassReader get(String var1);

   default Stream<ClassReader> getAncestorClasses(String name) {
      return StreamSupport.stream((() -> new Iterator<ClassReader>() {
         ClassReader currentClass = ClassReaderSource.this.get(name);

         public ClassReader next() {
            ClassReader result = this.currentClass;
            if (this.currentClass.getParent() != null) {
               this.currentClass = ClassReaderSource.this.get(this.currentClass.getParent());
            } else {
               this.currentClass = null;
            }

            return result;
         }

         @Override
         public boolean hasNext() {
            return this.currentClass != null;
         }
      }).spliterator(), false);
   }

   default Stream<ClassReader> getAncestors(String name) {
      return StreamSupport.stream((() -> new Iterator<ClassReader>() {
         Deque<Deque<ClassReader>> state = new ArrayDeque<>();
         private Set<ClassReader> visited = new HashSet<>();

         {
            this.state.push(new ArrayDeque<>());
            this.add(name);
         }

         public ClassReader next() {
            while (!this.state.isEmpty()) {
               Deque<ClassReader> level = this.state.peek();
               if (!level.isEmpty()) {
                  ClassReader result = level.removeFirst();
                  this.follow(result);
                  return result;
               }

               this.state.pop();
            }

            return null;
         }

         @Override
         public boolean hasNext() {
            return !this.state.stream().allMatch(e -> e.isEmpty());
         }

         private void follow(ClassReader cls) {
            this.state.push(new ArrayDeque<>());
            if (cls.getParent() != null) {
               this.add(cls.getParent());
            }

            for (String iface : cls.getInterfaces()) {
               this.add(iface);
            }
         }

         private void add(String namex) {
            ClassReader cls = ClassReaderSource.this.get(name);
            if (cls != null && this.visited.add(cls)) {
               this.state.peek().addLast(cls);
            }
         }
      }).spliterator(), false);
   }

   default MethodReader resolve(MethodReference method) {
      return this.getAncestors(method.getClassName()).map(cls -> cls.getMethod(method.getDescriptor())).filter(Objects::nonNull).findFirst().orElse(null);
   }

   default MethodReader resolveImplementation(MethodReference methodReference) {
      return ClassReaderSourceHelper.resolveMethodImplementation(this, methodReference.getClassName(), methodReference.getDescriptor(), new HashSet<>());
   }

   default MethodReader resolveImplementation(String className, MethodDescriptor descriptor) {
      return ClassReaderSourceHelper.resolveMethodImplementation(this, className, descriptor, new HashSet<>());
   }

   default FieldReader resolve(FieldReference field) {
      return this.getAncestors(field.getClassName()).map(cls -> cls.getField(field.getFieldName())).filter(Objects::nonNull).findFirst().orElse(null);
   }

   default Stream<MethodReader> overriddenMethods(MethodReference method) {
      return this.getAncestorClasses(method.getClassName()).map(cls -> cls.getMethod(method.getDescriptor())).filter(Objects::nonNull);
   }

   default Optional<Boolean> isSuperType(String superType, String subType) {
      return ClassReaderSourceHelper.isSuperType(this, superType, subType);
   }

   default MethodReader getMethod(MethodReference ref) {
      ClassReader cls = this.get(ref.getClassName());
      return cls == null ? null : cls.getMethod(ref.getDescriptor());
   }
}
