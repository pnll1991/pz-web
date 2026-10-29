package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class MethodReflectionInfoGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "annotationCount":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".annotations.length");
            break;
         case "annotation":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".annotations[");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append("]");
            break;
         case "genericReturnType":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".genericReturnType");
            break;
         case "parameterInfoCount":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".parameterInfos.length");
            break;
         case "parameterInfo":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".parameterInfos[");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append("]");
            break;
         case "typeParameterCount":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".typeParameters.length");
            break;
         case "typeParameter":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".typeParameters[");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append("]");
      }
   }
}
