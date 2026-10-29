package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.AnnotationConstructor;

public class AnnotationConstructorIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(AnnotationConstructor.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("reflection.h");
      context.writer().print("((TeaVM_AnnotationConstructor*) (");
      context.emit(invocation.getArguments().get(0));
      context.writer().print("))(");
      context.emit(invocation.getArguments().get(1));
      context.writer().print(")");
   }
}
