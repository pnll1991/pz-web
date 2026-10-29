package org.teavm.backend.wasm.model.instruction;

public class WasmInt31Reference extends WasmInstruction {
   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
