package org.teavm.backend.wasm.model.instruction;

public class WasmFloat64Constant extends WasmInstruction {
   private double value;

   public WasmFloat64Constant(double value) {
      this.value = value;
   }

   public double getValue() {
      return this.value;
   }

   public void setValue(double value) {
      this.value = value;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
