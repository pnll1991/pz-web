package org.teavm.model;

public class Variable implements VariableReader {
   private Program program;
   private int index;
   private int register;
   private String debugName;
   private String label;

   Variable(Program program) {
      this.program = program;
   }

   @Override
   public int getIndex() {
      return this.index;
   }

   void setIndex(int index) {
      this.index = index;
   }

   public Program getProgram() {
      return this.program;
   }

   void setProgram(Program program) {
      this.program = program;
   }

   @Override
   public int getRegister() {
      return this.register;
   }

   public void setRegister(int register) {
      this.register = register;
   }

   @Override
   public String getDebugName() {
      return this.debugName;
   }

   public void setDebugName(String debugName) {
      this.debugName = debugName;
   }

   @Override
   public String getLabel() {
      return this.label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getDisplayLabel() {
      return this.label != null ? this.label : String.valueOf(this.index);
   }
}
