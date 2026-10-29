package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.TypeVariableReferenceStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class TypeVariableReferenceIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public TypeVariableReferenceIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      TypeVariableReferenceStruct struct = this.classInfoProvider.reflectionTypes().typeVariableReference();
      context.generate(builder, invocation.getArguments().get(0));
      switch (invocation.getMethod().getName()) {
         case "level":
            builder.structGet(struct.structure(), struct.levelIndex());
            break;
         case "index":
            builder.structGet(struct.structure(), struct.indexIndex());
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
