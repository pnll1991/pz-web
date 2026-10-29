package org.teavm.backend.wasm.debug.info;

public class LineInfoLineCommand extends LineInfoCommand {
   private int line;

   public LineInfoLineCommand(int address, int line) {
      super(address);
      this.line = line;
   }

   public int line() {
      return this.line;
   }

   @Override
   public void acceptVisitor(LineInfoCommandVisitor visitor) {
      visitor.visit(this);
   }
}
