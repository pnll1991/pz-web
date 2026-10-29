package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.GenericArrayInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class GenericArrayInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public GenericArrayInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      GenericArrayInfoStruct struct = this.classInfoProvider.reflectionTypes().genericArrayInfo();
      switch (invocation.getMethod().getName()) {
         case "itemType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(struct.structure(), struct.itemTypeIndex());
            return;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
