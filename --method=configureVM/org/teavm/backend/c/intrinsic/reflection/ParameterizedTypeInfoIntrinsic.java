package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.ParameterizedTypeInfo;

public class ParameterizedTypeInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(ParameterizedTypeInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("reflection.h");
      switch (invocation.getMethod().getName()) {
         case "rawType":
            context.writer().print("((TeaVM_GenericTypeInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->parameterized.rawType");
            break;
         case "actualTypeArgumentCount":
            context.writer().print("teavm_reflection_parameterizedTypeArgumentCount((TeaVM_GenericTypeInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "actualTypeArgument":
            context.writer().print("((TeaVM_GenericTypeInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->parameterized.actualTypeArguments[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("]");
            break;
         case "ownerType":
            context.writer().print("((TeaVM_GenericTypeInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->parameterized.ownerType");
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
