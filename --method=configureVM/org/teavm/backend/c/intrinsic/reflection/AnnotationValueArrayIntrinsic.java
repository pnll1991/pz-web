package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.AnnotationValueArray;

public class AnnotationValueArrayIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(AnnotationValueArray.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "size":
            context.includes().addInclude("<stdint.h>");
            context.writer().print("(*((int32_t*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")))");
            break;
         case "getBoolean":
         case "getByte":
            this.writeArrayAccess(context, invocation, "TeaVM_ByteArray");
            break;
         case "getShort":
         case "getEnum":
            this.writeArrayAccess(context, invocation, "TeaVM_ShortArray");
            break;
         case "getChar":
            this.writeArrayAccess(context, invocation, "TeaVM_CharArray");
            break;
         case "getInt":
            this.writeArrayAccess(context, invocation, "TeaVM_IntArray");
            break;
         case "getLong":
            this.writeArrayAccess(context, invocation, "TeaVM_LongArray");
            break;
         case "getFloat":
            this.writeArrayAccess(context, invocation, "TeaVM_FloatArray");
            break;
         case "getDouble":
            this.writeArrayAccess(context, invocation, "TeaVM_DoubleArray");
            break;
         case "getString":
         case "getAnnotation":
            this.writeArrayAccess(context, invocation, "TeaVM_RefArray");
            break;
         case "getClass":
            context.writer().print("(&");
            this.writeArrayAccess(context, invocation, "TeaVM_ClassArray");
            context.writer().print(")");
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }

   private void writeArrayAccess(IntrinsicContext context, InvocationExpr invocation, String arrayType) {
      context.includes().includePath("reflection.h");
      context.writer().print("((" + arrayType + "*) (");
      context.emit(invocation.getArguments().get(0));
      context.writer().print("))->data[");
      context.emit(invocation.getArguments().get(1));
      context.writer().print("]");
   }
}
