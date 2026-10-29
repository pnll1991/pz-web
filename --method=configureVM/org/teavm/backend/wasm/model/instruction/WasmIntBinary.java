package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmIntBinary extends WasmInstruction {
   private WasmIntType type;
   private WasmIntBinaryOperation operation;

   public WasmIntBinary(WasmIntType type, WasmIntBinaryOperation operation) {
      this.type = Objects.requireNonNull(type);
      this.operation = Objects.requireNonNull(operation);
   }

   public WasmIntType getType() {
      return this.type;
   }

   public void setType(WasmIntType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmIntBinaryOperation getOperation() {
      return this.operation;
   }

   public void setOperation(WasmIntBinaryOperation operation) {
      this.operation = Objects.requireNonNull(operation);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
