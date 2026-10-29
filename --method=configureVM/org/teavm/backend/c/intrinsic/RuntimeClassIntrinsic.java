package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;
import org.teavm.runtime.RuntimeClass;

public class RuntimeClassIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals(RuntimeClass.class.getName())) {
         return false;
      }

      switch (method.getName()) {
         case "pack":
         case "unpack":
         case "first":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "pack":
            context.writer().print("TEAVM_PACK_CLASS(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")");
            break;
         case "unpack":
            context.writer().print("TEAVM_UNPACK_CLASS(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")");
            break;
         case "first":
            context.writer().print("teavm_firstClass");
      }
   }
}
