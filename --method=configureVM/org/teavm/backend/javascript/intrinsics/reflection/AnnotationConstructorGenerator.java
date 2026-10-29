package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class AnnotationConstructorGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      context.writeExpr(context.getArgument(0), Precedence.FUNCTION_CALL);
      context.getWriter().append("(");
      context.writeExpr(context.getArgument(1), Precedence.min());
      context.getWriter().append(")");
   }
}
