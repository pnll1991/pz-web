package org.teavm.backend.wasm.generate;

import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmGetLocal;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;

public class ValueCache {
   private TemporaryVariablePool tmpVars;

   public ValueCache(TemporaryVariablePool tmpVars) {
      this.tmpVars = tmpVars;
   }

   public CachedValue create(WasmType type, WasmInstructionBuilder builder) {
      WasmInstruction insn = builder.list.getLast();
      if (insn instanceof WasmGetLocal getLocal) {
         return new ValueCache.LocalVarCachedValue(getLocal.getLocal());
      } else if (insn instanceof WasmInt32Constant constExpr) {
         return new ValueCache.Int32CachedValue(constExpr.getValue());
      } else {
         WasmLocal tmpVar = this.tmpVars.acquire(type);
         builder.teeLocal(tmpVar);
         return new ValueCache.TmpVarCachedExpression(tmpVar);
      }
   }

   private static class Int32CachedValue extends CachedValue {
      private final int value;

      Int32CachedValue(int value) {
         this.value = value;
      }

      @Override
      public void emit(WasmInstructionBuilder builder) {
         builder.i32Const(this.value);
      }
   }

   private static class LocalVarCachedValue extends CachedValue {
      private final WasmLocal localVar;

      LocalVarCachedValue(WasmLocal localVar) {
         this.localVar = localVar;
      }

      @Override
      public void emit(WasmInstructionBuilder builder) {
         builder.getLocal(this.localVar);
      }
   }

   private class TmpVarCachedExpression extends CachedValue {
      private final WasmLocal tmpVar;

      TmpVarCachedExpression(WasmLocal tmpVar) {
         this.tmpVar = tmpVar;
      }

      @Override
      public void emit(WasmInstructionBuilder builder) {
         builder.getLocal(this.tmpVar);
      }

      @Override
      public void release() {
         ValueCache.this.tmpVars.release(this.tmpVar);
      }
   }
}
