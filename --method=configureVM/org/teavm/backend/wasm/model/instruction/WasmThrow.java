package org.teavm.backend.wasm.model.instruction;

import org.teavm.backend.wasm.model.WasmTag;

public class WasmThrow extends WasmInstruction {
   private WasmTag tag;

   public WasmThrow(WasmTag tag) {
      this.tag = tag;
   }

   public WasmTag getTag() {
      return this.tag;
   }

   public void setTag(WasmTag tag) {
      this.tag = tag;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }

   @Override
   public boolean isTerminating() {
      return true;
   }
}
