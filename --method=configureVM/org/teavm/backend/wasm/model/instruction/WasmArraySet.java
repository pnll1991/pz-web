package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmArray;

public class WasmArraySet extends WasmInstruction {
   private WasmArray type;

   public WasmArraySet(WasmArray type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmArray getType() {
      return this.type;
   }

   public void setType(WasmArray type) {
      this.type = Objects.requireNonNull(type);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
