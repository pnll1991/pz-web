package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmStructure;

public class WasmStructNewDefault extends WasmInstruction {
   private WasmStructure type;

   public WasmStructNewDefault(WasmStructure type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmStructure getType() {
      return this.type;
   }

   public void setType(WasmStructure type) {
      this.type = Objects.requireNonNull(type);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
