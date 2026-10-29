package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class AnnotationConstructorIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public AnnotationConstructorIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmFunctionType fnType = this.classInfoProvider.reflectionTypes().annotationInfo().constructorType();
      context.generate(builder, invocation.getArguments().get(1));
      context.generate(builder, invocation.getArguments().get(0));
      builder.callReference(fnType);
   }
}
