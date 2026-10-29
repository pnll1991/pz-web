package org.teavm.backend.wasm.dependencies;

import org.teavm.backend.wasm.runtime.WasmGCResources;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class WasmGCResourceDependency extends AbstractDependencyListener {
   private static final MethodReference ACQUIRE_METHOD = new MethodReference(WasmGCResources.class, "acquireResources", WasmGCResources.Resource[].class);

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (method.getMethod().getReference().equals(ACQUIRE_METHOD)) {
         MethodDependency create = agent.linkMethod(
            new MethodReference(WasmGCResources.class, "create", String.class, int.class, int.class, WasmGCResources.Resource.class)
         );
         create.propagate(1, agent.getType(ValueType.object("java.lang.String")));
         create.use();
         method.getResult().propagate(agent.getType(ValueType.arrayOf(ValueType.object(WasmGCResources.Resource.class.getName()))));
         method.getResult().getArrayItem().propagate(agent.getType(ValueType.object(WasmGCResources.Resource.class.getName())));
      }
   }
}
