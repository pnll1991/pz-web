package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmArray;

public class WasmArrayCopy extends WasmInstruction {
   private WasmArray targetArrayType;
   private WasmArray sourceArrayType;

   public WasmArrayCopy(WasmArray targetArrayType, WasmArray sourceArrayType) {
      this.targetArrayType = Objects.requireNonNull(targetArrayType);
      this.sourceArrayType = Objects.requireNonNull(sourceArrayType);
   }

   public WasmArray getTargetArrayType() {
      return this.targetArrayType;
   }

   public void setTargetArrayType(WasmArray targetArrayType) {
      this.targetArrayType = Objects.requireNonNull(targetArrayType);
   }

   public WasmArray getSourceArrayType() {
      return this.sourceArrayType;
   }

   public void setSourceArrayType(WasmArray sourceArrayType) {
      this.sourceArrayType = Objects.requireNonNull(sourceArrayType);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
