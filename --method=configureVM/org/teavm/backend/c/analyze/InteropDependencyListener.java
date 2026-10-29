package org.teavm.backend.c.analyze;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.Structure;
import org.teavm.model.AnnotationReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class InteropDependencyListener extends AbstractDependencyListener {
   @Override
   public void classReached(DependencyAgent agent, String className) {
      if (agent.getClassHierarchy().isSuperType(Structure.class.getName(), className, false)) {
         ClassReader cls = agent.getClassSource().get(className);
         if (cls != null) {
            this.reachFields(agent, cls);
         }
      }
   }

   private void reachFields(DependencyAgent agent, ClassReader cls) {
      for (FieldReader field : cls.getFields()) {
         if (!field.hasModifier(ElementModifier.STATIC)) {
            FieldDependency fieldDep = agent.linkField(field.getReference());
            this.reachType(agent, field.getType(), fieldDep.getValue());
         }
      }
   }

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (!method.isMissing() && method.getMethod().hasModifier(ElementModifier.NATIVE)) {
         AnnotationReader importAnnot = method.getMethod().getAnnotations().get(Import.class.getName());
         if (importAnnot != null) {
            MethodReference reference = method.getReference();
            if (reference.getReturnType().isObject("java.lang.String")) {
               method.getResult().propagate(agent.getType(ValueType.object("java.lang.String")));
            }

            for (int i = 0; i < reference.parameterCount(); i++) {
               this.reachType(agent, reference.parameterType(i), method.getVariable(i + 1));
            }
         }
      }
   }

   private void reachType(DependencyAgent agent, ValueType type, DependencyNode node) {
      if (type instanceof ValueType.Object) {
         String className = ((ValueType.Object)type).getClassName();
         if (agent.getClassHierarchy().isSuperType(Structure.class.getName(), className, false)) {
            agent.linkClass(className);
         }

         switch (className) {
            case "java.nio.Buffer":
            case "java.nio.ByteBuffer":
            case "java.nio.ShortBuffer":
            case "java.nio.CharBuffer":
            case "java.nio.IntBuffer":
            case "java.nio.LongBuffer":
            case "java.nio.FloatBuffer":
            case "java.nio.DoubleBuffer":
               MethodDependency getAddressMethod = agent.linkMethod(
                  new MethodReference("java.nio.NativeBufferUtil", "getAddress", ValueType.object("java.nio.Buffer"), ValueType.object(Address.class.getName()))
               );
               node.addConsumer(getAddressMethod.getVariable(1)::propagate);
               getAddressMethod.use();
         }
      }
   }
}
