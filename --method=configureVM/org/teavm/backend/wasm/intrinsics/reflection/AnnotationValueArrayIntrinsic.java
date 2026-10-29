package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;

public class AnnotationValueArrayIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public AnnotationValueArrayIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "size":
            context.generate(builder, invocation.getArguments().get(0));
            builder.arrayLength();
            break;
         case "getBoolean":
         case "getByte":
            this.get(invocation, context, builder, WasmStorageType.INT8, WasmSignedType.SIGNED);
            break;
         case "getShort":
            this.get(invocation, context, builder, WasmStorageType.INT16, WasmSignedType.SIGNED);
            break;
         case "getChar":
            this.get(invocation, context, builder, WasmStorageType.INT16, WasmSignedType.UNSIGNED);
            break;
         case "getInt":
            this.get(invocation, context, builder, WasmType.INT32.asStorage(), null);
            break;
         case "getLong":
            this.get(invocation, context, builder, WasmType.INT64.asStorage(), null);
            break;
         case "getFloat":
            this.get(invocation, context, builder, WasmType.FLOAT32.asStorage(), null);
            break;
         case "getDouble":
            this.get(invocation, context, builder, WasmType.FLOAT64.asStorage(), null);
            break;
         case "getClass": {
            WasmType.CompositeReference type = this.classInfoProvider.reflectionTypes().derivedClassInfo().structure().getReference();
            this.get(invocation, context, builder, type.asStorage(), null);
            break;
         }
         case "getEnum":
            this.get(invocation, context, builder, WasmStorageType.INT16, WasmSignedType.SIGNED);
            break;
         case "getString": {
            WasmType.CompositeReference type = this.classInfoProvider.getClassInfo("java.lang.String").getType();
            this.get(invocation, context, builder, type.asStorage(), null);
            break;
         }
         case "getAnnotation":
            this.get(invocation, context, builder, WasmType.STRUCT.asStorage(), null);
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }

   private void get(
      InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder, WasmStorageType type, WasmSignedType signedType
   ) {
      WasmArray array = this.classInfoProvider.reflectionTypes().arrayTypeOf(type);
      context.generate(builder, invocation.getArguments().get(0));
      builder.cast(array.getReference());
      context.generate(builder, invocation.getArguments().get(1));
      builder.arrayGet(array, signedType);
   }
}
