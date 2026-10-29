package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.Expr;
import org.teavm.backend.wasm.generate.TemporaryVariablePool;
import org.teavm.backend.wasm.generate.ValueCache;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.types.PreciseTypeInference;
import org.teavm.model.MethodReference;

public interface WasmGCInlineIntrinsicContext {
   void generate(WasmInstructionBuilder var1, Expr var2);

   PreciseTypeInference types();

   TemporaryVariablePool tempVars();

   ValueCache valueCache();

   boolean isAsync();

   boolean isAsyncMethod(MethodReference var1);

   MethodReference currentMethod();
}
