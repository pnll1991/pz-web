package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class ParameterInfoGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "genericType":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".genericType");
            break;
         case "annotationCount":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".annotations.length");
            break;
         case "annotation":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".annotations[");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append("]");
      }
   }
}
