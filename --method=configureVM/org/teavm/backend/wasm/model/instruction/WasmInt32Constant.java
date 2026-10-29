package org.teavm.backend.wasm.model.instruction;

public class WasmInt32Constant extends WasmInstruction {
   private int value;

   public WasmInt32Constant(int value) {
      this.value = value;
   }

   public int getValue() {
      return this.value;
   }

   public void setValue(int value) {
      this.value = value;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
