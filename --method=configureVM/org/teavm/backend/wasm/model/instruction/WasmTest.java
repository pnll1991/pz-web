package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmType;

public class WasmTest extends WasmInstruction {
   private WasmType.Reference testType;

   public WasmTest(WasmType.Reference testType) {
      this.testType = Objects.requireNonNull(testType);
   }

   public WasmType.Reference getTestType() {
      return this.testType;
   }

   public void setTestType(WasmType.Reference testType) {
      this.testType = Objects.requireNonNull(testType);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
