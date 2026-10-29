package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmNumType;

public class WasmConversion extends WasmInstruction {
   private WasmNumType sourceType;
   private WasmNumType targetType;
   private boolean signed;
   private boolean reinterpret;
   private boolean nonTrapping;

   public WasmConversion(WasmNumType sourceType, WasmNumType targetType, boolean signed) {
      this.sourceType = Objects.requireNonNull(sourceType);
      this.targetType = Objects.requireNonNull(targetType);
      this.signed = signed;
   }

   public WasmNumType getSourceType() {
      return this.sourceType;
   }

   public void setSourceType(WasmNumType sourceType) {
      this.sourceType = Objects.requireNonNull(sourceType);
   }

   public WasmNumType getTargetType() {
      return this.targetType;
   }

   public void setTargetType(WasmNumType targetType) {
      this.targetType = Objects.requireNonNull(targetType);
   }

   public boolean isSigned() {
      return this.signed;
   }

   public void setSigned(boolean signed) {
      this.signed = signed;
   }

   public boolean isReinterpret() {
      return this.reinterpret;
   }

   public void setReinterpret(boolean reinterpret) {
      this.reinterpret = reinterpret;
   }

   public boolean isNonTrapping() {
      return this.nonTrapping;
   }

   public void setNonTrapping(boolean nonTrapping) {
      this.nonTrapping = nonTrapping;
   }

   @Override
   public void acceptVisitor(WasmInstructionVisitor visitor) {
      visitor.visit(this);
   }
}
