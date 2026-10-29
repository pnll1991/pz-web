package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmLocal;

public class WasmTeeLocal extends WasmInstruction {
   private WasmLocal local;

   public WasmTeeLocal(WasmLocal local) {
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
