package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.AnnotationInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class AnnotationInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public AnnotationInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      AnnotationInfoStruct annotInfoStruct = this.classInfoProvider.reflectionTypes().annotationInfo();
      context.generate(builder, invocation.getArguments().get(0));
      switch (invocation.getMethod().getName()) {
         case "data":
            builder.structGet(annotInfoStruct.structure(), annotInfoStruct.dataIndex());
            break;
         case "constructor":
            builder.structGet(annotInfoStruct.structure(), annotInfoStruct.constructorIndex());
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
