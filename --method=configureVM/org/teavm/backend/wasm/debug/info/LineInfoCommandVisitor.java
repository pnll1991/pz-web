package org.teavm.backend.wasm.debug.info;

public interface LineInfoCommandVisitor {
   void visit(LineInfoEnterCommand var1);

   void visit(LineInfoExitCommand var1);

   void visit(LineInfoFileCommand var1);

   void visit(LineInfoLineCommand var1);
}
