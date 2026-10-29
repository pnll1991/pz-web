package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class StringInfoGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      context.getWriter().appendFunction("$rt_str").append("(");
      context.writeExpr(context.getArgument(0));
      context.getWriter().append(")");
   }
}
