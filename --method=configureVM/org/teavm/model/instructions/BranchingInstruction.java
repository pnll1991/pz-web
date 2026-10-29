package org.teavm.model.instructions;

import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class BranchingInstruction extends Instruction {
   private Variable operand;
   private BranchingCondition condition;
   private BasicBlock consequent;
   private BasicBlock alternative;

   public BranchingInstruction(BranchingCondition condition) {
      this.condition = condition;
   }

   public Variable getOperand() {
      return this.operand;
   }

   public void setOperand(Variable operand) {
      this.operand = operand;
   }

   public BranchingCondition getCondition() {
      return this.condition;
   }

   public void setCondition(BranchingCondition condition) {
      this.condition = condition;
   }

   public BasicBlock getConsequent() {
      return this.consequent;
   }

   public void setConsequent(BasicBlock consequent) {
      this.consequent = consequent;
   }

   public BasicBlock getAlternative() {
      return this.alternative;
   }

   public void setAlternative(BasicBlock alternative) {
      this.alternative = alternative;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
