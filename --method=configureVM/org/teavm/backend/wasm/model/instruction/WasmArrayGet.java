package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmArray;

public class WasmArrayGet extends WasmInstruction {
   private WasmArray type;
   private WasmSignedType signedType;

   public WasmArrayGet(WasmArray type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmArray getType() {
      return this.type;
   }

   public void setType(WasmArray type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmSignedType getSignedType() {
      return this.signedType;
   }

   public void setSignedType(WasmSignedType signedType) {
      this.signedType = signedType;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
