package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;

public class WasmIntUnary extends WasmInstruction {
   private WasmIntType type;
   private WasmIntUnaryOperation operation;

   public WasmIntUnary(WasmIntType type, WasmIntUnaryOperation operation) {
      this.type = Objects.requireNonNull(type);
      this.operation = Objects.requireNonNull(operation);
   }

   public WasmIntType getType() {
      return this.type;
   }

   public void setType(WasmIntType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmIntUnaryOperation getOperation() {
      return this.operation;
   }

   public void setOperation(WasmIntUnaryOperation operation) {
      this.operation = Objects.requireNonNull(operation);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
