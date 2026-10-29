package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmType;

public class WasmCastBranch extends WasmInstruction {
   private WasmCastCondition condition;
   private WasmType.Reference sourceType;
   private WasmType.Reference targetType;
   private WasmInstructionList target;

   public WasmCastBranch(WasmCastCondition condition, WasmType.Reference sourceType, WasmType.Reference targetType, WasmInstructionList target) {
      this.condition = Objects.requireNonNull(condition);
      this.sourceType = Objects.requireNonNull(sourceType);
      this.targetType = Objects.requireNonNull(targetType);
      this.target = Objects.requireNonNull(target);
   }

   public WasmCastCondition getCondition() {
      return this.condition;
   }

   public void setCondition(WasmCastCondition condition) {
      this.condition = Objects.requireNonNull(condition);
   }

   public WasmType.Reference getSourceType() {
      return this.sourceType;
   }

   public void setSourceType(WasmType.Reference sourceType) {
      this.sourceType = Objects.requireNonNull(sourceType);
   }

   public WasmType.Reference getTargetType() {
      return this.targetType;
   }

   public void setTargetType(WasmType.Reference targetType) {
      this.targetType = Objects.requireNonNull(targetType);
   }

   public WasmInstructionList getTarget() {
      return this.target;
   }

   public void setTarget(WasmInstructionList target) {
      this.target = Objects.requireNonNull(target);
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
