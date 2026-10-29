package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.TypeVariableInfo;

public class TypeVariableInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(TypeVariableInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("reflection.h");
      switch (invocation.getMethod().getName()) {
         case "name":
            context.writer().print("((TeaVM_TypeVariableInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->name");
            break;
         case "boundCount":
            context.writer().print("teavm_reflection_typeVariableBoundCount((TeaVM_TypeVariableInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "bound":
            context.writer().print("((TeaVM_TypeVariableInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->bounds[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("]");
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
