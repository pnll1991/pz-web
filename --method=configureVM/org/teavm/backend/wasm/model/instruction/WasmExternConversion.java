package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmExternConversion extends WasmInstruction {
   private WasmExternConversionType type;

   public WasmExternConversion(WasmExternConversionType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmExternConversionType getType() {
      return this.type;
   }

   public void setType(WasmExternConversionType type) {
      this.type = Objects.requireNonNull(type);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
