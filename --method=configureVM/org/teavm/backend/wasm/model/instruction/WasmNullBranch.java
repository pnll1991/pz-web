package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmNullBranch extends WasmInstruction {
   private WasmNullCondition condition;
   private WasmInstructionList target;

   public WasmNullBranch(WasmNullCondition condition, WasmInstructionList target) {
      this.condition = Objects.requireNonNull(condition);
      this.target = Objects.requireNonNull(target);
   }

   public WasmNullCondition getCondition() {
      return this.condition;
   }

   public void setCondition(WasmNullCondition condition) {
      this.condition = Objects.requireNonNull(condition);
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
