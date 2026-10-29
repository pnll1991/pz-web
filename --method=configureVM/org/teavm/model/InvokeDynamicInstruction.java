package org.teavm.model;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.instructions.InstructionVisitor;

public class InvokeDynamicInstruction extends Instruction {
   private MethodDescriptor method;
   private MethodHandle bootstrapMethod;
   private List<RuntimeConstant> bootstrapArguments = new ArrayList<>();
   private Variable instance;
   private List<Variable> arguments = new ArrayList<>();
   private Variable receiver;

   public MethodDescriptor getMethod() {
      return this.method;
   }

   public void setMethod(MethodDescriptor method) {
      this.method = method;
   }

   public MethodHandle getBootstrapMethod() {
      return this.bootstrapMethod;
   }

   public void setBootstrapMethod(MethodHandle bootstrapMethod) {
      this.bootstrapMethod = bootstrapMethod;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public List<RuntimeConstant> getBootstrapArguments() {
      return this.bootstrapArguments;
   }

   public Variable getInstance() {
      return this.instance;
   }

   public void setInstance(Variable instance) {
      this.instance = instance;
   }

   public List<Variable> getArguments() {
      return this.arguments;
   }

   @Override
   public void acceptVisitor(InstructionVisitor visitor) {
      visitor.visit(this);
   }
}
