package org.teavm.backend.wasm.disasm;

import org.teavm.backend.wasm.parser.CodeListener;
import org.teavm.backend.wasm.parser.GlobalSectionListener;
import org.teavm.backend.wasm.parser.WasmHollowType;

public class DisassemblyGlobalSectionListener extends BaseDisassemblyListener implements GlobalSectionListener {
   private DisassemblyCodeListener codeListener;

   public DisassemblyGlobalSectionListener(DisassemblyWriter writer, NameProvider nameProvider) {
      super(writer, nameProvider);
      this.codeListener = new DisassemblyCodeListener(writer, nameProvider);
   }

   @Override
   public CodeListener startGlobal(int index, WasmHollowType type, boolean mutable) {
      this.writer.address().write("(global ");
      this.writer.startLinkTarget("g" + index).write("(; ").write(String.valueOf(index)).write(" ;)");
      String name = this.nameProvider.global(index);
      if (name != null) {
         this.writer.write(" $").write(name);
      }

      this.writer.endLinkTarget().write(" ");
      if (mutable) {
         this.writer.write("(mut ");
         this.writeType(type);
         this.writer.write(")");
      } else {
         this.writeType(type);
      }

      this.writer.indent().eol();
      this.codeListener.reset();
      return this.codeListener;
   }

   @Override
   public void endGlobal() {
      this.writer.outdent().write(")").eol();
   }
}
