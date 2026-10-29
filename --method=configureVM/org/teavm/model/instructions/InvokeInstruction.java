package org.teavm.model.instructions;

import java.util.AbstractList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Variable;

public class InvokeInstruction extends Instruction {
   private InvocationType type;
   private MethodReference method;
   private Variable instance;
   private Variable[] arguments;
   private Variable receiver;
   private List<? extends Variable> argumentList = new AbstractList<Variable>() {
      public Variable get(int index) {
         if (InvokeInstruction.this.arguments != null && index < InvokeInstruction.this.arguments.length) {
            return InvokeInstruction.this.arguments[index];
         } else {
            throw new IndexOutOfBoundsException();
         }
      }

      @Override
      public int size() {
         return InvokeInstruction.this.arguments != null ? InvokeInstruction.this.arguments.length : 0;
      }
   };

   public InvocationType getType() {
      return this.type;
   }

   public void setType(InvocationType type) {
      this.type = type;
   }

   public Variable getInstance() {
      return this.instance;
   }

   public void setInstance(Variable instance) {
      this.instance = instance;
   }

   public List<? extends Variable> getArguments() {
      return this.argumentList;
   }

   public void replaceArguments(UnaryOperator<Variable> f) {
      if (this.arguments != null) {
         for (int i = 0; i < this.arguments.length; i++) {
            this.arguments[i] = f.apply(this.arguments[i]);
         }
      }
   }

   public void setArguments(Variable... arguments) {
      this.arguments = arguments.length > 0 ? (Variable[])arguments.clone() : null;
   }

   public MethodReference getMethod() {
      return this.method;
   }

   public void setMethod(MethodReference method) {
      this.method = method;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
