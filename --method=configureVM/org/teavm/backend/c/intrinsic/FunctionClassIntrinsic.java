package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.interop.Function;
import org.teavm.model.MethodReference;

public class FunctionClassIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(Function.class.getName()) && method.getName().equals("isNull");
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.writer().print("(");
      context.emit(invocation.getArguments().get(0));
      context.writer().print(" == NULL");
      context.writer().print(")");
   }
}
