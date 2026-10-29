package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.DerivedClassInfo;

public class DerivedClassInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(DerivedClassInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "classInfo":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_ClassPtr*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->baseClass");
            break;
         case "arrayDegree":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_ClassPtr*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->arrayDegree");
      }
   }
}
