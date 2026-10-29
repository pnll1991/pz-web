package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.ClassReflectionInfo;

public class ClassReflectionInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(ClassReflectionInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "fieldCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_fieldCount((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "field":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->fields->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("])");
            break;
         case "methodCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_methodCount((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "method":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->methods->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("])");
            break;
         case "annotationCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_annotationCount((TeaVM_ClassReflection*) ");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")");
            break;
         case "annotation":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->annotations->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("])");
            break;
         case "typeParameterCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_typeParameterCount((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "typeParameter":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->typeParameters->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("])");
            break;
         case "innerClassCount":
            context.includes().includePath("reflection.h");
            context.writer().print("teavm_reflection_innerClassCount((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))");
            break;
         case "innerClass":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_ClassReflection*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->innerClasses->data[");
            context.emit(invocation.getArguments().get(1));
            context.writer().print("]");
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
