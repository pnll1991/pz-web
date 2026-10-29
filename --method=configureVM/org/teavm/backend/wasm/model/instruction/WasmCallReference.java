package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmFunctionType;

public class WasmCallReference extends WasmInstruction {
   private WasmFunctionType type;
   private boolean suspend;

   public WasmCallReference(WasmFunctionType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmFunctionType getType() {
      return this.type;
   }

   public void setType(WasmFunctionType type) {
      this.type = Objects.requireNonNull(type);
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
