package org.teavm.backend.wasm.dependencies;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.MethodReference;

public class SystemArrayCopyDependencySupport extends AbstractDependencyListener {
   private static final MethodReference COPY_METHOD = new MethodReference(
      System.class, "arraycopy", Object.class, int.class, Object.class, int.class, int.class, void.class
   );

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (method.getMethod().getReference().equals(COPY_METHOD)) {
         MethodDependency implMethod = agent.linkMethod(
            new MethodReference(System.class, "arrayCopyImpl", Object.class, int.class, Object.class, int.class, int.class, void.class)
         );
         method.getVariable(1).connect(implMethod.getVariable(1));
         method.getVariable(3).connect(implMethod.getVariable(3));
         implMethod.use();
      }
   }
}
