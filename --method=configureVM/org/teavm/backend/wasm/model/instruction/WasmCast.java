package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmType;

public class WasmCast extends WasmInstruction {
   private WasmType.Reference targetType;

   public WasmCast(WasmType.Reference targetType) {
      this.targetType = Objects.requireNonNull(targetType);
   }

   public WasmType.Reference getTargetType() {
      return this.targetType;
   }

   public void setTargetType(WasmType.Reference targetType) {
      this.targetType = Objects.requireNonNull(targetType);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
