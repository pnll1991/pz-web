package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.RawTypeInfo;

public class RawTypeInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(RawTypeInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("reflection.h");
      switch (invocation.getMethod().getName()) {
         case "rawType":
            context.writer().print("&((TeaVM_GenericTypeInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->classPtr");
            return;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
