package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.generate.reflection.ParameterInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class ParameterInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public ParameterInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      ParameterInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().parameterInfo();
      switch (invocation.getMethod().getName()) {
         case "annotationCount":
            WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
               context.generate(b, invocation.getArguments().get(0));
               b.structGet(infoStruct.structure(), infoStruct.annotationsIndex());
            });
            break;
         case "annotation":
            WasmArray array = this.classInfoProvider.reflectionTypes().annotationInfo().array();
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.annotationsIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(array);
            break;
         case "genericType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.genericTypeIndex());
            break;
         default:
            throw new IllegalStateException(invocation.getMethod().getName());
      }
   }
}
