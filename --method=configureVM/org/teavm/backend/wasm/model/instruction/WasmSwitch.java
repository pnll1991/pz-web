package org.teavm.backend.wasm.model.instruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WasmSwitch extends WasmInstruction {
   private List<WasmInstructionList> targets = new ArrayList<>();
   private WasmInstructionList defaultTarget;

   public WasmSwitch(WasmInstructionList defaultTarget) {
      this.defaultTarget = Objects.requireNonNull(defaultTarget);
   }

   public List<WasmInstructionList> getTargets() {
      return this.targets;
   }

   public WasmInstructionList getDefaultTarget() {
      return this.defaultTarget;
   }

   public void setDefaultTarget(WasmInstructionList defaultTarget) {
      this.defaultTarget = Objects.requireNonNull(defaultTarget);
   }

   @Override
   public boolean isTerminating() {
      return true;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
