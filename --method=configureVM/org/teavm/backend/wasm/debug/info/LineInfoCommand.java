package org.teavm.backend.wasm.debug.info;

public abstract class LineInfoCommand {
   private int address;

   public LineInfoCommand(int address) {
      this.address = address;
   }

   public int address() {
      return this.address;
   }

   public abstract void acceptVisitor(LineInfoCommandVisitor var1);
}
