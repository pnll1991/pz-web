package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmGlobal;

public class WasmGetGlobal extends WasmInstruction {
   private WasmGlobal global;

   public WasmGetGlobal(WasmGlobal global) {
      this.global = Objects.requireNonNull(global);
   }

   public WasmGlobal getGlobal() {
      return this.global;
   }

   public void setGlobal(WasmGlobal global) {
      this.global = Objects.requireNonNull(global);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
