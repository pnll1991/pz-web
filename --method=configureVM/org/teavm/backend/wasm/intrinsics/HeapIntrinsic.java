package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;

public class HeapIntrinsic implements WasmGCInlineIntrinsic {
   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmLocal pagesVar = context.tempVars().acquire(WasmType.INT32);
      context.generate(builder, invocation.getArguments().get(0));
      builder.i32Const(1)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB)
         .i32Const(65536)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.DIV_UNSIGNED)
         .i32Const(1)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD)
         .setLocal(pagesVar)
         .getLocal(pagesVar)
         .memoryGrow()
         .drop()
         .getLocal(pagesVar)
         .i32Const(65536)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.MUL);
      context.tempVars().release(pagesVar);
   }
}
