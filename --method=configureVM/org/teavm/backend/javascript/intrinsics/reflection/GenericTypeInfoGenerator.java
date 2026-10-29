package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class GenericTypeInfoGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getDescriptor().getName()) {
         case "kind":
         case "rawType":
         case "ownerType":
         case "level":
         case "index":
         case "itemType":
         case "bound":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".").append(methodRef.getDescriptor().getName());
            break;
         case "actualTypeArgumentCount":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".actualTypeArguments.length");
            break;
         case "actualTypeArgument":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".actualTypeArguments[");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append("]");
            break;
         case "asParameterizedType":
         case "asTypeVariable":
         case "asGenericArray":
         case "asWildcard":
         case "asRawType":
            context.writeExpr(context.getArgument(0), context.getPrecedence());
      }
   }
}
