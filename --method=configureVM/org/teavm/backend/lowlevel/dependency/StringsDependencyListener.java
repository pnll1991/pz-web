package org.teavm.backend.lowlevel.dependency;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class StringsDependencyListener extends AbstractDependencyListener {
   private static final String STRINGS_CLASS = "org.teavm.interop.Strings";

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      MethodReference ref = method.getReference();
      if (ref.getClassName().equals("org.teavm.interop.Strings")) {
         switch (ref.getName()) {
            case "fromC":
            case "fromC16":
               method.getResult().propagate(agent.getType(ValueType.object("java.lang.String")));
         }
      }
   }
}
