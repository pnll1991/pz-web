package org.teavm.backend.wasm.model.instruction;

public class WasmIsNull extends WasmInstruction {
   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
