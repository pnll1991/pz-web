package org.teavm.backend.wasm.debug.info;

public class LineInfoExitCommand extends LineInfoCommand {
   public LineInfoExitCommand(int address) {
      super(address);
   }

   @Override
   public void acceptVisitor(LineInfoCommandVisitor visitor) {
      visitor.visit(this);
   }
}
