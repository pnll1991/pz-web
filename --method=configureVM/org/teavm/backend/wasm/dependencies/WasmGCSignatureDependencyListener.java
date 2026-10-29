package org.teavm.backend.wasm.dependencies;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.ValueType;

public class WasmGCSignatureDependencyListener extends AbstractDependencyListener {
   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      for (int i = 0; i < method.getMethod().parameterCount(); i++) {
         this.linkType(agent, method.getMethod().parameterType(i));
      }

      this.linkType(agent, method.getMethod().getResultType());
   }

   @Override
   public void fieldReached(DependencyAgent agent, FieldDependency field) {
      this.linkType(agent, field.getField().getType());
   }

   private void linkType(DependencyAgent agent, ValueType type) {
      if (type instanceof ValueType.Object) {
         agent.linkClass(((ValueType.Object)type).getClassName());
      } else if (type instanceof ValueType.Array) {
         this.linkType(agent, ((ValueType.Array)type).getItemType());
      }
   }
}
