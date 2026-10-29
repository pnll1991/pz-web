package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;
import org.teavm.runtime.ExceptionHandling;

public class ExceptionHandlingIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals(ExceptionHandling.class.getName())) {
         return false;
      }

      switch (method.getName()) {
         case "findCallSiteById":
         case "isJumpSupported":
         case "jumpToFrame":
         case "abort":
         case "isObfuscated":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "findCallSiteById":
            context.writer().print("TEAVM_FIND_CALLSITE(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(", ");
            context.emit(invocation.getArguments().get(1));
            context.writer().print(")");
            break;
         case "isJumpSupported":
            context.writer().print("TEAVM_JUMP_SUPPORTED");
            break;
         case "jumpToFrame":
            context.writer().print("TEAVM_JUMP_TO_FRAME(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(", ");
            context.emit(invocation.getArguments().get(1));
            context.writer().print(")");
            break;
         case "abort":
            context.includes().addInclude("<stdlib.h>");
            context.writer().print("abort();");
            break;
         case "isObfuscated":
            context.writer().print("TEAVM_OBFUSCATED");
      }
   }
}
