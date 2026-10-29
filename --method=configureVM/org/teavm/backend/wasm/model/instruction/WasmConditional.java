package org.teavm.backend.wasm.model.instruction;

import org.teavm.backend.wasm.model.WasmBlockType;

public class WasmConditional extends WasmInstruction {
   private WasmBlockType type;
   private final WasmInstructionList thenBlock = new WasmInstructionList(this);
   private final WasmInstructionList elseBlock = new WasmInstructionList(this);

   public WasmBlockType getType() {
      return this.type;
   }

   public void setType(WasmBlockType type) {
      this.type = type;
   }

   public WasmInstructionList getThenBlock() {
      return this.thenBlock;
   }

   public WasmInstructionList getElseBlock() {
      return this.elseBlock;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
