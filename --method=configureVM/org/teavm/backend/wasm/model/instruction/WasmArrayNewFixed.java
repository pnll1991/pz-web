package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmArray;

public class WasmArrayNewFixed extends WasmInstruction {
   private WasmArray type;
   private int size;

   public WasmArrayNewFixed(WasmArray type, int size) {
      this.type = Objects.requireNonNull(type);
      this.size = size;
   }

   public WasmArray getType() {
      return this.type;
   }

   public void setType(WasmArray type) {
      this.type = Objects.requireNonNull(type);
   }

   public int getSize() {
      return this.size;
   }

   public void setSize(int size) {
      this.size = size;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
