package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.GenericTypeInfo;

public class GenericTypeInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(GenericTypeInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("reflection.h");
      switch (invocation.getMethod().getName()) {
         case "kind":
            context.writer().print("((TeaVM_GenericTypeInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->kind");
            break;
         case "asParameterizedType":
         case "asTypeVariable":
         case "asGenericArray":
         case "asWildcard":
         case "asRawType":
            context.emit(invocation.getArguments().get(0));
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
