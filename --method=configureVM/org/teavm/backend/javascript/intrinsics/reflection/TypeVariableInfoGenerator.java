package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class TypeVariableInfoGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "name":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".name");
            break;
         case "boundCount":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".bounds.length");
            break;
         case "bound":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".bounds[");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append("]");
      }
   }
}
