package org.teavm.backend.wasm.dependencies;

import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class StringInternDependencySupport extends AbstractDependencyListener {
   private static final MethodReference STRING_INTERN = new MethodReference(String.class, "intern", String.class);

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (method.getMethod().getReference().equals(STRING_INTERN)) {
         MethodDependency query = agent.linkMethod(new MethodReference(StringInternPool.class, "query", String.class, String.class));
         query.getVariable(1).propagate(agent.getType(ValueType.object("java.lang.String")));
         query.use();
         String entryTypeName = StringInternPool.class.getName() + "$Entry";
         MethodDependency remove = agent.linkMethod(
            new MethodReference(StringInternPool.class.getName(), "remove", ValueType.object(entryTypeName), ValueType.VOID)
         );
         remove.getVariable(1).propagate(agent.getType(ValueType.object(entryTypeName)));
         remove.use();
         MethodDependency clinit = agent.linkMethod(new MethodReference(StringInternPool.class, "<clinit>", void.class));
         clinit.use();
         MethodDependency getValue = agent.linkMethod(
            new MethodReference(StringInternPool.class.getName() + "$Entry", "getValue", ValueType.parse(String.class))
         );
         getValue.getResult().propagate(agent.getType(ValueType.object(String.class.getName())));
      }
   }
}
