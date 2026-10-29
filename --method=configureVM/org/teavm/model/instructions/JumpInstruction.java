package org.teavm.model.instructions;

import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;

public class JumpInstruction extends Instruction {
   private BasicBlock target;

   public BasicBlock getTarget() {
      return this.target;
   }

   public void setTarget(BasicBlock target) {
      this.target = target;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
