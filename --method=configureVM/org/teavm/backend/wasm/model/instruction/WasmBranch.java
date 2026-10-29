package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmBranch extends WasmInstruction {
   private WasmInstructionList target;

   public WasmBranch(WasmInstructionList target) {
      this.target = Objects.requireNonNull(target);
   }

   public WasmInstructionList getTarget() {
      return this.target;
   }

   public void setTarget(WasmInstructionList target) {
      this.target = Objects.requireNonNull(target);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
