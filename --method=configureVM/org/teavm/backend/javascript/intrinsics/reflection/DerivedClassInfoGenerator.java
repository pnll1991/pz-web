package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class DerivedClassInfoGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "classInfo":
            context.writeExpr(context.getArgument(0), context.getPrecedence());
            break;
         case "arrayDegree":
            context.getWriter().append("0");
      }
   }
}
