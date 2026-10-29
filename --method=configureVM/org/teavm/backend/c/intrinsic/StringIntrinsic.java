package org.teavm.backend.c.intrinsic;

import org.teavm.ast.Expr;
import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;

public class StringIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(String.class.getName()) && method.getName().equals("intern");
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      Expr arg = invocation.getArguments().get(0);
      context.includes().includePath("stringhash.h");
      context.writer().print("((void*) teavm_internString((TeaVM_String*) (");
      context.emit(arg);
      context.writer().print(")))");
   }
}
