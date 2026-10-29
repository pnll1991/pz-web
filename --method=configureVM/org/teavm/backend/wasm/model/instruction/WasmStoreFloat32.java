package org.teavm.backend.wasm.model.instruction;

public class WasmStoreFloat32 extends WasmInstruction {
   private int alignment;
   private int offset;

   public WasmStoreFloat32(int alignment) {
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
