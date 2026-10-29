package org.teavm.backend.wasm.debug.info;

public class LineInfoCommandExecutor implements LineInfoCommandVisitor {
   private FileInfo file;
   private int line = 1;
   private int address;
   private InliningLocation inliningLocation;

   public InstructionLocation createLocation() {
      return new InstructionLocation(this.address, this.file != null ? new Location(this.file, this.line, this.inliningLocation) : null);
   }

   @Override
   public void visit(LineInfoEnterCommand command) {
      this.address = command.address();
      this.inliningLocation = new InliningLocation(new Location(this.file, this.line, this.inliningLocation), command.method());
      this.file = null;
      this.line = 1;
   }

   @Override
   public void visit(LineInfoExitCommand command) {
      this.address = command.address();
      this.file = this.inliningLocation.location().file();
      this.line = this.inliningLocation.location().line();
      this.inliningLocation = this.inliningLocation.location().inlining();
   }

   @Override
   public void visit(LineInfoFileCommand command) {
      this.address = command.address();
      this.file = command.file();
      this.line = command.line();
   }

   @Override
   public void visit(LineInfoLineCommand command) {
      this.address = command.address();
      this.line = command.line();
   }
}
