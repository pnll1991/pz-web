package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmStructure;

public class WasmStructSet extends WasmInstruction {
   private WasmStructure type;
   private int fieldIndex;

   public WasmStructSet(WasmStructure type, int fieldIndex) {
      this.type = Objects.requireNonNull(type);
      this.fieldIndex = fieldIndex;
   }

   public WasmStructure getType() {
      return this.type;
   }

   public void setType(WasmStructure type) {
      this.type = Objects.requireNonNull(type);
   }

   public int getFieldIndex() {
      return this.fieldIndex;
   }

   public void setFieldIndex(int fieldIndex) {
      this.fieldIndex = fieldIndex;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
