package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmStoreInt32 extends WasmInstruction {
   private int alignment;
   private int offset;
   private WasmInt32Subtype convertTo;

   public WasmStoreInt32(int alignment, WasmInt32Subtype convertTo) {
      this.alignment = alignment;
      this.convertTo = Objects.requireNonNull(convertTo);
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

   public WasmInt32Subtype getConvertTo() {
      return this.convertTo;
   }

   public void setConvertTo(WasmInt32Subtype convertTo) {
      this.convertTo = Objects.requireNonNull(convertTo);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
