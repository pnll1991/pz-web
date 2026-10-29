package org.teavm.backend.wasm.model.instruction;

public class WasmUnreachable extends WasmInstruction {
   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }

   @Override
   public boolean isTerminating() {
      return true;
   }
}
