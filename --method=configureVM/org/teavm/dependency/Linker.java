package org.teavm.dependency;

import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;

public class Linker {
   private DependencyInfo dependency;

   public Linker(DependencyInfo dependency) {
      this.dependency = dependency;
   }

   public void link(ClassHolder cls) {
      for (MethodHolder method : cls.getMethods().toArray(new MethodHolder[0])) {
         MethodReference methodRef = method.getReference();
         MethodDependencyInfo methodDep = this.dependency.getMethod(methodRef);
         if (methodDep == null || !methodDep.isUsed()) {
            if (method.hasModifier(ElementModifier.STATIC)) {
               cls.removeMethod(method);
            } else {
               method.getModifiers().add(ElementModifier.ABSTRACT);
               method.getModifiers().remove(ElementModifier.NATIVE);
               method.setProgram(null);
            }
         }
      }

      for (FieldHolder field : cls.getFields().toArray(new FieldHolder[0])) {
         FieldReference fieldRef = new FieldReference(cls.getName(), field.getName());
         if (this.dependency.getField(fieldRef) == null) {
            cls.removeField(field);
         }
      }
   }
}
