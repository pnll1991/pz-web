package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmLocal;

public class WasmSetLocal extends WasmInstruction {
   private WasmLocal local;

   public WasmSetLocal(WasmLocal local) {
      this.local = Objects.requireNonNull(local);
   }

   public WasmLocal getLocal() {
      return this.local;
   }

   public void setLocal(WasmLocal local) {
      this.local = Objects.requireNonNull(local);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
