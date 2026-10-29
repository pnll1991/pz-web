package org.teavm.backend.wasm.model.instruction;

public class WasmArrayLength extends WasmInstruction {
   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
