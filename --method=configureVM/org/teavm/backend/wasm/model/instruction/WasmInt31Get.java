package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmInt31Get extends WasmInstruction {
   private WasmSignedType signedType;

   public WasmInt31Get(WasmSignedType signedType) {
      this.signedType = Objects.requireNonNull(signedType);
   }

   public WasmSignedType getSignedType() {
      return this.signedType;
   }

   public void setSignedType(WasmSignedType signedType) {
      this.signedType = Objects.requireNonNull(signedType);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
