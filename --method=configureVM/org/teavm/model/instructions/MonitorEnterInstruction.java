package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class MonitorEnterInstruction extends Instruction {
   private Variable objectRef;

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }

   public Variable getObjectRef() {
      return this.objectRef;
   }

   public void setObjectRef(Variable objectRef) {
      this.objectRef = objectRef;
   }
}
