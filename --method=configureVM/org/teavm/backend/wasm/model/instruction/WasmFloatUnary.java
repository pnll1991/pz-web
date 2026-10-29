package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmFloatUnary extends WasmInstruction {
   private WasmFloatType type;
   private WasmFloatUnaryOperation operation;

   public WasmFloatUnary(WasmFloatType type, WasmFloatUnaryOperation operation) {
      this.type = Objects.requireNonNull(type);
      this.operation = Objects.requireNonNull(operation);
   }

   public WasmFloatType getType() {
      return this.type;
   }

   public void setType(WasmFloatType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmFloatUnaryOperation getOperation() {
      return this.operation;
   }

   public void setOperation(WasmFloatUnaryOperation operation) {
      this.operation = Objects.requireNonNull(operation);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
