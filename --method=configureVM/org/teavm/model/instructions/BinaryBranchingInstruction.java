package org.teavm.model.instructions;

import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class BinaryBranchingInstruction extends Instruction {
   private Variable firstOperand;
   private Variable secondOperand;
   private BinaryBranchingCondition condition;
   private BasicBlock consequent;
   private BasicBlock alternative;

   public BinaryBranchingInstruction(BinaryBranchingCondition condition) {
      this.condition = condition;
   }

   public Variable getFirstOperand() {
      return this.firstOperand;
   }

   public void setFirstOperand(Variable firstOperand) {
      this.firstOperand = firstOperand;
   }

   public Variable getSecondOperand() {
      return this.secondOperand;
   }

   public void setSecondOperand(Variable secondOperand) {
      this.secondOperand = secondOperand;
   }

   public BinaryBranchingCondition getCondition() {
      return this.condition;
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
