package org.teavm.backend.wasm.debug.info;

public class LineInfoFileCommand extends LineInfoCommand {
   private FileInfo file;
   private int line;

   public LineInfoFileCommand(int address, FileInfo file, int line) {
      super(address);
      this.file = file;
      this.line = line;
   }

   public FileInfo file() {
      return this.file;
   }

   public int line() {
      return this.line;
   }

   @Override
   public void acceptVisitor(LineInfoCommandVisitor visitor) {
      visitor.visit(this);
   }
}
