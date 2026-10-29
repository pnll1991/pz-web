package org.teavm.backend.wasm.model.instruction;

public class WasmInt64Constant extends WasmInstruction {
   private long value;

   public WasmInt64Constant(long value) {
      this.value = value;
   }

   public long getValue() {
      return this.value;
   }

   public void setValue(long value) {
      this.value = value;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
