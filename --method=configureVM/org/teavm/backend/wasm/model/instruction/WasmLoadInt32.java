package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmLoadInt32 extends WasmInstruction {
   private int alignment;
   private int offset;
   private WasmInt32Subtype convertFrom;

   public WasmLoadInt32(int alignment, WasmInt32Subtype convertFrom) {
      this.alignment = alignment;
      this.convertFrom = Objects.requireNonNull(convertFrom);
   }

   public int getAlignment() {
      return this.alignment;
   }

   public void setAlignment(int alignment) {
      this.alignment = alignment;
   }

   public int getOffset() {
      return this.offset;
   }

   public void setOffset(int offset) {
      this.offset = offset;
   }

   public WasmInt32Subtype getConvertFrom() {
      return this.convertFrom;
   }

   public void setConvertFrom(WasmInt32Subtype convertFrom) {
      this.convertFrom = Objects.requireNonNull(convertFrom);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
