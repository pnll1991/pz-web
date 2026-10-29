package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.MethodInfo;

public class MethodInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(MethodInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "name":
         case "modifiers":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_MethodInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->").print(invocation.getMethod().getName());
            break;
         case "returnType":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_MethodInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->returnType)");
            break;
         case "parameterCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_methodParameterCount((TeaVM_MethodInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "parameterType":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_MethodInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->parameterTypes->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("])");
            break;
         case "checkedExceptionCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_methodCheckedExceptionCount((TeaVM_MethodInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "checkedExceptionType":
            context.includes().includePath("reflection.h");
            context.writer().print("(((TeaVM_MethodInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->checkedExceptionTypes->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("])");
            break;
         case "reflection":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_MethodInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->reflection");
            break;
         case "call":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_callMethod(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(", ");
            context.emit(invocation.getArguments().get(1));
            context.writer().print(", ");
            context.emit(invocation.getArguments().get(2));
            context.writer().print(")");
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
