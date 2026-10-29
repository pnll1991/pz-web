package org.teavm.model.classes;

import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class VirtualTableEntry {
   private MethodDescriptor method;
   MethodReference implementor;
   private int index;

   VirtualTableEntry(MethodDescriptor method, MethodReference implementor, int index) {
      this.method = method;
      this.implementor = implementor;
      this.index = index;
   }

   public MethodDescriptor getMethod() {
      return this.method;
   }

   public MethodReference getImplementor() {
      return this.implementor;
   }

   public int getIndex() {
      return this.index;
   }
}
