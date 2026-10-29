package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.WildcardTypeInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class WildcardTypeInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public WildcardTypeInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WildcardTypeInfoStruct struct = this.classInfoProvider.reflectionTypes().wildcardTypeInfo();
      switch (invocation.getMethod().getName()) {
         case "bound":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(struct.structure(), struct.boundIndex());
            return;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
