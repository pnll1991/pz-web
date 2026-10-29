package org.teavm.model;

public class Outgoing {
   private Sigma sigma;
   private Variable value;
   private BasicBlock target;

   public Outgoing(Variable value, BasicBlock target) {
      this.value = value;
      this.target = target;
   }

   public Sigma getSigma() {
      return this.sigma;
   }

   void setSigma(Sigma sigma) {
      this.sigma = sigma;
   }

   public Variable getValue() {
      return this.value;
   }

   public void setValue(Variable value) {
      this.value = value;
   }

   public BasicBlock getTarget() {
      return this.target;
   }

   public void setTarget(BasicBlock target) {
      this.target = target;
   }
}
