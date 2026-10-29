package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmStoreInt64 extends WasmInstruction {
   private int alignment;
   private int offset;
   private WasmInt64Subtype convertTo;

   public WasmStoreInt64(int alignment, WasmInt64Subtype convertTo) {
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

   public WasmInt64Subtype getConvertTo() {
      return this.convertTo;
   }

   public void setConvertTo(WasmInt64Subtype convertTo) {
      this.convertTo = Objects.requireNonNull(convertTo);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
