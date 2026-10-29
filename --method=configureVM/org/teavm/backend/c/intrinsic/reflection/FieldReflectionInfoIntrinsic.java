package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.FieldReflectionInfo;

public class FieldReflectionInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(FieldReflectionInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "annotationCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_fieldReflectionAnnotationCount((TeaVM_FieldReflectionInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "annotation":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_FieldReflectionInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->annotations->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("])");
            break;
         case "genericType":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_fieldGenericType((TeaVM_FieldReflectionInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
