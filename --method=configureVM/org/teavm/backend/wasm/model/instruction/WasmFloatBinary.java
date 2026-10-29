package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmFloatBinary extends WasmInstruction {
   private WasmFloatType type;
   private WasmFloatBinaryOperation operation;

   public WasmFloatBinary(WasmFloatType type, WasmFloatBinaryOperation operation) {
      this.type = Objects.requireNonNull(type);
      this.operation = Objects.requireNonNull(operation);
   }

   public WasmFloatType getType() {
      return this.type;
   }

   public void setType(WasmFloatType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmFloatBinaryOperation getOperation() {
      return this.operation;
   }

   public void setOperation(WasmFloatBinaryOperation operation) {
      this.operation = Objects.requireNonNull(operation);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
