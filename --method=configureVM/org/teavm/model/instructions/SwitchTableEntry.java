package org.teavm.model.instructions;

import org.teavm.model.BasicBlock;

public class SwitchTableEntry implements SwitchTableEntryReader {
   private int condition;
   private BasicBlock target;
   private SwitchInstruction instruction;

   public SwitchInstruction getInstruction() {
      return this.instruction;
   }

   void setInstruction(SwitchInstruction instruction) {
      this.instruction = instruction;
   }

   @Override
   public int getCondition() {
      return this.condition;
   }

   public void setCondition(int condition) {
      this.condition = condition;
   }

   public BasicBlock getTarget() {
      return this.target;
   }

   public void setTarget(BasicBlock target) {
      this.target = target;
   }
}
