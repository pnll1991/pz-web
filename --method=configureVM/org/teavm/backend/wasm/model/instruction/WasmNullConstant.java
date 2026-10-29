package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmType;

public class WasmNullConstant extends WasmInstruction {
   private WasmType.Reference type;

   public WasmNullConstant(WasmType.Reference type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmType.Reference getType() {
      return this.type;
   }

   public void setType(WasmType.Reference type) {
      this.type = Objects.requireNonNull(type);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
