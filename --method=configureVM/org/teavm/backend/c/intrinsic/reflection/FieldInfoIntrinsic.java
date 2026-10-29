package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.reflect.FieldInfo;

public class FieldInfoIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(FieldInfo.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "name":
         case "modifiers":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_FieldInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->").print(invocation.getMethod().getName());
            break;
         case "type":
            context.includes().includePath("reflection.h");
            context.writer().print("(&((TeaVM_FieldInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->type)");
            break;
         case "read":
            this.generateReadField(context, invocation, "teavm_reflection_readField");
            break;
         case "readAsByte":
         case "readAsBoolean":
            this.generateReadField(context, invocation, "teavm_reflection_readFieldAsByte");
            break;
         case "readAsShort":
            this.generateReadField(context, invocation, "teavm_reflection_readFieldAsShort");
            break;
         case "readAsChar":
            this.generateReadField(context, invocation, "teavm_reflection_readFieldAsChar");
            break;
         case "readAsInt":
            this.generateReadField(context, invocation, "teavm_reflection_readFieldAsInt");
            break;
         case "readAsLong":
            this.generateReadField(context, invocation, "teavm_reflection_readFieldAsLong");
            break;
         case "readAsFloat":
            this.generateReadField(context, invocation, "teavm_reflection_readFieldAsFloat");
            break;
         case "readAsDouble":
            this.generateReadField(context, invocation, "teavm_reflection_readFieldAsDouble");
            break;
         case "write":
            if (invocation.getMethod().parameterType(1) instanceof ValueType.Primitive primitiveType) {
               switch (primitiveType.getKind()) {
                  case BOOLEAN:
                  case BYTE:
                     this.generateWriteField(context, invocation, "teavm_reflection_writeFieldAsByte");
                     return;
                  case CHARACTER:
                     this.generateWriteField(context, invocation, "teavm_reflection_writeFieldAsChar");
                     return;
                  case SHORT:
                     this.generateWriteField(context, invocation, "teavm_reflection_writeFieldAsChar");
                     return;
                  case INTEGER:
                     this.generateWriteField(context, invocation, "teavm_reflection_writeFieldAsInt");
                     return;
                  case LONG:
                     this.generateWriteField(context, invocation, "teavm_reflection_writeFieldAsLong");
                     return;
                  case FLOAT:
                     this.generateWriteField(context, invocation, "teavm_reflection_writeFieldAsFloat");
                     return;
                  case DOUBLE:
                     this.generateWriteField(context, invocation, "teavm_reflection_writeFieldAsDouble");
               }
            } else {
               this.generateWriteField(context, invocation, "teavm_reflection_writeField");
            }
            break;
         case "reflection":
            context.includes().includePath("reflection.h");
            context.writer().print("((TeaVM_FieldInfo*) (");
            context.emit(invocation.getArguments().get(0));
            context.writer().print("))->reflection");
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }

   private void generateReadField(IntrinsicContext context, InvocationExpr invocation, String functionName) {
      context.includes().includePath("reflection.h");
      context.writer().print(functionName).print("(");
      context.emit(invocation.getArguments().get(1));
      context.writer().print(", (TeaVM_FieldInfo*)");
      context.emit(invocation.getArguments().get(0));
      context.writer().print(")");
   }

   private void generateWriteField(IntrinsicContext context, InvocationExpr invocation, String functionName) {
      context.includes().includePath("reflection.h");
      context.writer().print(functionName).print("(");
      context.emit(invocation.getArguments().get(1));
      context.writer().print(", (TeaVM_FieldInfo*) ");
      context.emit(invocation.getArguments().get(0));
      context.writer().print(", ");
      context.emit(invocation.getArguments().get(2));
      context.writer().print(")");
   }
}
