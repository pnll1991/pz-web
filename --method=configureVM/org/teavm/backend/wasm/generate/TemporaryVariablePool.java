package org.teavm.backend.wasm.generate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;

public class TemporaryVariablePool {
   private WasmFunction function;
   private Map<WasmType, Deque<WasmLocal>> temporaryVariablesByType = new HashMap<>();

   public TemporaryVariablePool(WasmFunction function) {
      this.function = function;
   }

   public WasmLocal acquire(WasmType type) {
      Deque<WasmLocal> stack = this.temporaryVariablesByType.computeIfAbsent(type, k -> new ArrayDeque<>());
      WasmLocal variable = stack.pollFirst();
      if (variable == null) {
         variable = new WasmLocal(type);
         this.function.add(variable);
      }

      return variable;
   }

   public void release(WasmLocal variable) {
      Deque<WasmLocal> stack = this.temporaryVariablesByType.get(variable.getType());
      stack.push(variable);
   }
}
