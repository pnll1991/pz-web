package org.teavm.backend.wasm.model.instruction;

import org.teavm.backend.wasm.model.WasmBlockType;

public class WasmBlock extends WasmInstruction {
   private boolean loop;
   private WasmBlockType type;
   private WasmInstructionList body = new WasmInstructionList(this);

   public WasmBlock(boolean loop) {
      this.loop = loop;
   }

   public WasmInstructionList getBody() {
      return this.body;
   }

   public boolean isLoop() {
      return this.loop;
   }

   public void setLoop(boolean loop) {
      this.loop = loop;
   }

   public WasmBlockType getType() {
      return this.type;
   }

   public void setType(WasmBlockType type) {
      this.type = type;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
