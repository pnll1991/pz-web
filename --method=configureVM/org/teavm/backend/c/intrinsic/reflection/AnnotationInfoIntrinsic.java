package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.AnnotationInfo;

public class AnnotationInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(AnnotationInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "data":
         case "constructor":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_AnnotationInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->").print(invocation.getMethod().getName());
            return;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
