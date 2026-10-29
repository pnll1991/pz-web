package org.teavm.backend.wasm.disasm;

import java.io.PrintWriter;

public class DisassemblyTextWriter extends DisassemblyWriter {
   public DisassemblyTextWriter(PrintWriter out) {
      super(out);
   }

   @Override
   public DisassemblyWriter startLink(String s) {
      return this;
   }

   @Override
   public DisassemblyWriter endLink() {
      return this;
   }

   @Override
   public DisassemblyWriter startLinkTarget(String s) {
      return this;
   }

   @Override
   public DisassemblyWriter endLinkTarget() {
      return this;
   }

   @Override
   public DisassemblyWriter prologue() {
      return this;
   }

   @Override
   public DisassemblyWriter epilogue() {
      return this;
   }
}
