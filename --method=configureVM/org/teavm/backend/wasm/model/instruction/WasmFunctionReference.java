package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmFunction;

public class WasmFunctionReference extends WasmInstruction {
   private WasmFunction function;

   public WasmFunctionReference(WasmFunction function) {
      this.function = Objects.requireNonNull(function);
   }

   public WasmFunction getFunction() {
      return this.function;
   }

   public void setFunction(WasmFunction function) {
      this.function = Objects.requireNonNull(function);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
