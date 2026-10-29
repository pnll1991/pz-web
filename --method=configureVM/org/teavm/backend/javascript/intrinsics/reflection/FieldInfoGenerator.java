package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class FieldInfoGenerator implements Injector {
   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "name":
         case "modifiers":
         case "type":
         case "reflection":
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            context.getWriter().append(".").append(methodRef.getName());
            break;
         case "readAsBoolean":
         case "readAsByte":
         case "readAsShort":
         case "readAsChar":
         case "readAsInt":
         case "readAsLong":
         case "readAsFloat":
         case "readAsDouble":
            context.getWriter().appendFunction("$rt_getRawFieldValue").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(")");
            break;
         case "read":
            context.getWriter().appendFunction("$rt_getFieldValue").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(")");
            break;
         case "write":
            String functionName = methodRef.parameterType(1) instanceof ValueType.Primitive ? "$rt_setRawFieldValue" : "$rt_setFieldValue";
            context.getWriter().appendFunction(functionName).append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(2), Precedence.min());
            context.getWriter().append(")");
      }
   }
}
