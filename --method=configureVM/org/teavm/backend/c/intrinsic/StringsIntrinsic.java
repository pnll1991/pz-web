package org.teavm.backend.c.intrinsic;

import org.teavm.ast.Expr;
import org.teavm.ast.InvocationExpr;
import org.teavm.interop.Strings;
import org.teavm.model.MethodReference;

public class StringsIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(Strings.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "toC": {
            Expr arg = invocation.getArguments().get(0);
            context.writer().print("teavm_stringToC(");
            context.emit(arg);
            context.writer().print(")");
            break;
         }
         case "fromC":
            context.writer().print("teavm_cToString(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")");
            break;
         case "toC16": {
            Expr arg = invocation.getArguments().get(0);
            context.writer().print("teavm_stringToC16(");
            context.emit(arg);
            context.writer().print(")");
            break;
         }
         case "fromC16":
            context.writer().print("teavm_c16ToString(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")");
      }
   }
}
