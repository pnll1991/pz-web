package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;

public class IntegerIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals(Integer.class.getName())) {
         return false;
      }

      switch (method.getName()) {
         case "divideUnsigned":
         case "remainderUnsigned":
         case "compareUnsigned":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "divideUnsigned":
            this.writeBinary(context, invocation, "/");
            break;
         case "remainderUnsigned":
            this.writeBinary(context, invocation, "%");
            break;
         case "compareUnsigned":
            context.writer().print("teavm_compare_u32(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(", ");
            context.emit(invocation.getArguments().get(1));
            context.writer().print(")");
      }
   }

   private void writeBinary(IntrinsicContext context, InvocationExpr invocation, String operation) {
      context.writer().print("((int32_t) ((uint32_t) ");
      context.emit(invocation.getArguments().get(0));
      context.writer().print(" " + operation + " (uint32_t) ");
      context.emit(invocation.getArguments().get(1));
      context.writer().print("))");
   }
}
