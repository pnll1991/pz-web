package org.teavm.vm;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.MethodReference;

public class StdlibDependencyListener extends AbstractDependencyListener {
   private static final MethodReference ARRAY_COPY_METHOD = new MethodReference(
      System.class, "arraycopy", Object.class, int.class, Object.class, int.class, int.class, void.class
   );
   private static final MethodReference FAST_ARRAY_COPY_METHOD = new MethodReference(
      System.class, "fastArraycopy", Object.class, int.class, Object.class, int.class, int.class, void.class
   );
   private boolean reached;

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (!this.reached && method.getReference().equals(ARRAY_COPY_METHOD)) {
         this.reached = true;
         agent.linkMethod(FAST_ARRAY_COPY_METHOD).use();
      }
   }
}
