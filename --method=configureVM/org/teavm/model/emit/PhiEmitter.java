package org.teavm.model.emit;

import org.teavm.model.Phi;

public class PhiEmitter {
   Phi phi;
   ValueEmitter value;

   PhiEmitter(Phi phi, ValueEmitter value) {
      this.phi = phi;
      this.value = value;
   }

   public ValueEmitter getValue() {
      return this.value;
   }
}
