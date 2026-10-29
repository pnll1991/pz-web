package org.teavm.backend.c.intrinsic.reflection;

import java.util.Objects;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.reflect.AnnotationData;
import org.teavm.runtime.reflect.DerivedClassInfo;

public class AnnotationDataIntrinsic implements Intrinsic {
   private ClassReaderSource classes;

   public AnnotationDataIntrinsic(ClassReaderSource classes) {
      this.classes = classes;
   }

   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().endsWith("$$_nativeData")) {
         return false;
      }

      ClassReader cls = this.classes.get(method.getClassName());
      return cls == null ? false : Objects.equals(cls.getParent(), AnnotationData.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      if (invocation.getMethod().getName().equals("constructor") && invocation.getArguments().isEmpty()) {
         int lengthWithoutSuffix = invocation.getMethod().getClassName().length() - "$$_nativeData".length();
         String baseName = invocation.getMethod().getClassName().substring(0, lengthWithoutSuffix);
         String implName = baseName + "$$_impl";
         MethodReference ctor = new MethodReference(implName, "create", ValueType.object(invocation.getMethod().getClassName()), ValueType.object(implName));
         context.writer().print(context.names().forMethod(ctor));
      } else {
         context.includes().includeClass(invocation.getMethod().getClassName());
         boolean needRef = false;
         if (invocation.getMethod().getReturnType() instanceof ValueType.Object) {
            String className = ((ValueType.Object)invocation.getMethod().getReturnType()).getClassName();
            if (className.equals(DerivedClassInfo.class.getName())) {
               needRef = true;
            }
         }

         if (needRef) {
            context.writer().print("(&");
         }

         context.writer().print("((").print(context.names().forClass(invocation.getMethod().getClassName())).print("*) (");
         context.emit(invocation.getArguments().get(0));
         context.writer().print("))->");
         String fieldName = context.names().forMemberField(new FieldReference(invocation.getMethod().getClassName(), invocation.getMethod().getName()));
         context.writer().print(fieldName);
         if (needRef) {
            context.writer().print(")");
         }
      }
   }
}
