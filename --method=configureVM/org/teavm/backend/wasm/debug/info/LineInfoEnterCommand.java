package org.teavm.backend.wasm.debug.info;

public class LineInfoEnterCommand extends LineInfoCommand {
   private MethodInfo method;

   public LineInfoEnterCommand(int address, MethodInfo method) {
      super(address);
      this.method = method;
   }

   public MethodInfo method() {
      return this.method;
   }

   @Override
   public void acceptVisitor(LineInfoCommandVisitor visitor) {
      visitor.visit(this);
   }
}
