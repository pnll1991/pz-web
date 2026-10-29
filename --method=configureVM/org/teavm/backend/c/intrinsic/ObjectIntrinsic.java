package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;

public class ObjectIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return !method.getClassName().equals(Object.class.getName()) ? false : method.getName().equals("getObjectClassInfo");
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("core.h");
      context.writer().print("TEAVM_CLASS_OF(");
      context.emit(invocation.getArguments().get(0));
      context.writer().print(")");
   }
}
