package org.teavm.backend.wasm.model.instruction;

import java.util.ArrayList;
import java.util.List;
import org.teavm.backend.wasm.model.WasmType;

public class WasmTry extends WasmInstruction {
   private WasmType type;
   private WasmInstructionList body = new WasmInstructionList(this);
   private List<WasmCatchClause> catches = new ArrayList<>();

   public WasmInstructionList getBody() {
      return this.body;
   }

   public WasmType getType() {
      return this.type;
   }

   public void setType(WasmType type) {
      this.type = type;
   }

   public List<WasmCatchClause> getCatches() {
      return this.catches;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
