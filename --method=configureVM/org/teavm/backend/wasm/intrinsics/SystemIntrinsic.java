package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class SystemIntrinsic implements WasmGCInlineIntrinsic {
   private WasmFunctionTypes functionTypes;
   private WasmModule module;
   private WasmFunction workerFunction;

   public SystemIntrinsic(WasmFunctionTypes functionTypes, WasmModule module) {
      this.functionTypes = functionTypes;
      this.module = module;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      if (this.workerFunction == null) {
         this.workerFunction = new WasmFunction(this.functionTypes.of(WasmType.FLOAT64));
         this.workerFunction.setName("teavm@currentTimeMillis");
         this.workerFunction.setImportName("currentTimeMillis");
         this.workerFunction.setImportModule("teavmDate");
         this.module.functions.add(this.workerFunction);
      }

      builder.call(this.workerFunction).convert(WasmNumType.FLOAT64, WasmNumType.INT64, true);
   }
}
