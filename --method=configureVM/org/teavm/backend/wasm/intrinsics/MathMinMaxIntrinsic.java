package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.model.instruction.WasmFloatBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmFloatType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class MathMinMaxIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmFloatType type;

   public MathMinMaxIntrinsic(WasmFloatType type) {
      this.type = type;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmFloatBinaryOperation operation = invocation.getMethod().getName().equals("min") ? WasmFloatBinaryOperation.MIN : WasmFloatBinaryOperation.MAX;
      context.generate(builder, invocation.getArguments().get(0));
      context.generate(builder, invocation.getArguments().get(1));
      builder.floatBinary(this.type, operation);
   }
}
