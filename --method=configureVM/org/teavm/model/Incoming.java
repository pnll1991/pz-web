package org.teavm.model;

public class Incoming implements IncomingReader {
   private Phi phi;
   private Variable value;
   private BasicBlock source;

   public Variable getValue() {
      return this.value;
   }

   public void setValue(Variable value) {
      this.value = value;
   }

   public BasicBlock getSource() {
      return this.source;
   }

   public void setSource(BasicBlock source) {
      this.source = source;
   }

   public Phi getPhi() {
      return this.phi;
   }

   void setPhi(Phi phi) {
      this.phi = phi;
   }
}
