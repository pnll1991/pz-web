package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmFunction;

public class WasmCall extends WasmInstruction {
   private WasmFunction function;
   private boolean suspend;

   public WasmCall(WasmFunction function) {
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

   @Override
   public boolean isSuspend() {
      return this.suspend;
   }

   public void setSuspend(boolean suspend) {
      this.suspend = suspend;
   }
}
