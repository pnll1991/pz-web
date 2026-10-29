package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public interface WasmGCInlineIntrinsic {
   void apply(InvocationExpr var1, WasmGCInlineIntrinsicContext var2, WasmInstructionBuilder var3);
}
