package org.teavm.backend.wasm.model.instruction;

public class WasmLoadFloat64 extends WasmInstruction {
   private int alignment;
   private int offset;

   public WasmLoadFloat64(int alignment) {
      this.alignment = alignment;
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

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
