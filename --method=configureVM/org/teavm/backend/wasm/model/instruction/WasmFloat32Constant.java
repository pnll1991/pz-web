package org.teavm.backend.wasm.model.instruction;

public class WasmFloat32Constant extends WasmInstruction {
   private float value;

   public WasmFloat32Constant(float value) {
      this.value = value;
   }

   public float getValue() {
      return this.value;
   }

   public void setValue(float value) {
      this.value = value;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
