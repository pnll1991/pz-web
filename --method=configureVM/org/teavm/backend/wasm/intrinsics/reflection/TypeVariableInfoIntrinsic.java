package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.generate.reflection.TypeVariableInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class TypeVariableInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public TypeVariableInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      TypeVariableInfoStruct struct = this.classInfoProvider.reflectionTypes().typeVariableInfo();
      switch (invocation.getMethod().getName()) {
         case "name":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(struct.structure(), struct.nameIndex());
            break;
         case "boundCount":
            WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
               context.generate(b, invocation.getArguments().get(0));
               b.structGet(struct.structure(), struct.boundsIndex());
            });
            break;
         case "bound":
            WasmArray array = this.classInfoProvider.reflectionTypes().genericTypeArray();
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(struct.structure(), struct.boundsIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(array);
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
