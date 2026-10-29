package org.teavm.model.instructions;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class SwitchInstruction extends Instruction {
   private Variable condition;
   private List<SwitchTableEntry> entries = new ArrayList<>();
   private BasicBlock defaultTarget;
   private List<SwitchTableEntry> safeEntries = new AbstractList<SwitchTableEntry>() {
      public SwitchTableEntry set(int index, SwitchTableEntry element) {
         SwitchTableEntry oldElement = SwitchInstruction.this.entries.get(index);
         oldElement.setInstruction(null);
         SwitchInstruction.this.entries.set(index, element);
         element.setInstruction(SwitchInstruction.this);
         return oldElement;
      }

      public void add(int index, SwitchTableEntry element) {
         SwitchInstruction.this.entries.add(index, element);
         element.setInstruction(SwitchInstruction.this);
      }

      public SwitchTableEntry remove(int index) {
         SwitchTableEntry element = SwitchInstruction.this.entries.remove(index);
         element.setInstruction(null);
         return element;
      }

      @Override
      public void clear() {
         for (SwitchTableEntry element : SwitchInstruction.this.entries) {
            element.setInstruction(null);
         }

         SwitchInstruction.this.entries.clear();
      }

      public SwitchTableEntry get(int index) {
         return SwitchInstruction.this.entries.get(index);
      }

      @Override
      public int size() {
         return SwitchInstruction.this.entries.size();
      }
   };

   public Variable getCondition() {
      return this.condition;
   }

   public void setCondition(Variable condition) {
      this.condition = condition;
   }

   public List<SwitchTableEntry> getEntries() {
      return this.safeEntries;
   }

   public BasicBlock getDefaultTarget() {
      return this.defaultTarget;
   }

   public void setDefaultTarget(BasicBlock defaultTarget) {
      this.defaultTarget = defaultTarget;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
