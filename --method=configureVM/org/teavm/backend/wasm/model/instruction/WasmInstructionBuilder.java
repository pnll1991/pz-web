package org.teavm.backend.wasm.model.instruction;

import java.util.ArrayList;
import java.util.List;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmBlockType;
import org.teavm.backend.wasm.model.WasmCompositeType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.model.TextLocation;

public class WasmInstructionBuilder {
   public final WasmTypeInference typeInference = new WasmTypeInference();
   public final WasmInstructionList list;
   private List<TextLocation> locationStack = new ArrayList<>();
   private TextLocation currentLocation;

   public WasmInstructionBuilder(WasmInstructionList list) {
      this.list = list;
   }

   public WasmInstructionBuilder pushLocation(TextLocation location) {
      this.locationStack.add(this.currentLocation);
      if (location != null) {
         this.currentLocation = location;
      }

      return this;
   }

   public WasmInstructionBuilder popLocation() {
      this.currentLocation = this.locationStack.remove(this.locationStack.size() - 1);
      return this;
   }

   public WasmInstructionBuilder unreachable() {
      return this.add(new WasmUnreachable());
   }

   public WasmInstructionBuilder block() {
      return this.block(null, false);
   }

   public WasmInstructionBuilder block(WasmBlockType type) {
      return this.block(type, false);
   }

   public WasmInstructionBuilder block(WasmType type) {
      return this.block(type != null ? type.asBlock() : null, false);
   }

   public WasmInstructionBuilder loop() {
      return this.block(null, true);
   }

   public WasmInstructionBuilder loop(WasmBlockType type) {
      return this.block(type, true);
   }

   public WasmInstructionBuilder loop(WasmType type) {
      return this.block(type != null ? type.asBlock() : null, true);
   }

   private WasmInstructionBuilder block(WasmBlockType type, boolean loop) {
      WasmBlock block = new WasmBlock(loop);
      block.setType(type);
      this.add(block);
      WasmInstructionBuilder inner = new WasmInstructionBuilder(block.getBody());
      if (type != null) {
         inner.typeInference.typeStack.addAll(type.getInputTypes());
      }

      inner.currentLocation = this.currentLocation;
      return inner;
   }

   public WasmConditional conditional() {
      return this.conditional((WasmBlockType)null);
   }

   public WasmConditional conditional(WasmBlockType type) {
      WasmConditional cond = new WasmConditional();
      cond.setType(type);
      this.add(cond);
      return cond;
   }

   public WasmConditional conditional(WasmType type) {
      return this.conditional(type != null ? type.asBlock() : null);
   }

   public WasmInstructionBuilder branch(WasmInstructionList target) {
      return this.add(new WasmBranch(target));
   }

   public WasmInstructionBuilder branch(WasmInstructionBuilder target) {
      return this.branch(target.list);
   }

   public WasmInstructionBuilder nullBranch(WasmNullCondition condition, WasmInstructionList target) {
      return this.add(new WasmNullBranch(condition, target));
   }

   public WasmInstructionBuilder nullBranch(WasmNullCondition condition, WasmInstructionBuilder target) {
      return this.nullBranch(condition, target.list);
   }

   public WasmInstructionBuilder castBranch(
      WasmCastCondition condition, WasmType.Reference sourceType, WasmType.Reference targetType, WasmInstructionList target
   ) {
      return this.add(new WasmCastBranch(condition, sourceType, targetType, target));
   }

   public WasmInstructionBuilder castBranch(
      WasmCastCondition condition, WasmType.Reference sourceType, WasmType.Reference targetType, WasmInstructionBuilder target
   ) {
      return this.castBranch(condition, sourceType, targetType, target.list);
   }

   public WasmInstructionBuilder breakTo(WasmInstructionList target) {
      return this.add(new WasmBreak(target));
   }

   public WasmInstructionBuilder breakTo(WasmInstructionBuilder target) {
      return this.breakTo(target.list);
   }

   public WasmSwitch switch_(WasmInstructionList defaultTarget) {
      WasmSwitch insn = new WasmSwitch(defaultTarget);
      this.add(insn);
      return insn;
   }

   public WasmSwitch switch_(WasmInstructionBuilder defaultTarget) {
      return this.switch_(defaultTarget.list);
   }

   public WasmInstructionBuilder return_() {
      return this.add(new WasmReturn());
   }

   public WasmInstructionBuilder i32Const(int value) {
      return this.add(new WasmInt32Constant(value));
   }

   public WasmInstructionBuilder i64Const(long value) {
      return this.add(new WasmInt64Constant(value));
   }

   public WasmInstructionBuilder f32Const(float value) {
      return this.add(new WasmFloat32Constant(value));
   }

   public WasmInstructionBuilder f64Const(double value) {
      return this.add(new WasmFloat64Constant(value));
   }

   public WasmInstructionBuilder nullConst(WasmType.Reference type) {
      return this.add(new WasmNullConstant(type));
   }

   public WasmInstructionBuilder isNull() {
      return this.add(new WasmIsNull());
   }

   public WasmInstructionBuilder getLocal(WasmLocal local) {
      if (this.list.getLast() instanceof WasmSetLocal) {
         WasmSetLocal setLocal = (WasmSetLocal)this.list.getLast();
         if (setLocal.getLocal() == local) {
            WasmTeeLocal tee = new WasmTeeLocal(local);
            tee.setLocation(this.list.getLast().getLocation());
            this.list.getLast().delete();
            this.list.add(tee);
            this.typeInference.typeStack.add(local.getType());
            return this;
         }
      }

      return this.add(new WasmGetLocal(local));
   }

   public WasmInstructionBuilder setLocal(WasmLocal local) {
      return this.add(new WasmSetLocal(local));
   }

   public WasmInstructionBuilder teeLocal(WasmLocal local) {
      return this.add(new WasmTeeLocal(local));
   }

   public WasmInstructionBuilder getGlobal(WasmGlobal global) {
      return this.add(new WasmGetGlobal(global));
   }

   public WasmInstructionBuilder setGlobal(WasmGlobal global) {
      return this.add(new WasmSetGlobal(global));
   }

   public WasmInstructionBuilder intBinary(WasmIntType type, WasmIntBinaryOperation operation) {
      return this.add(new WasmIntBinary(type, operation));
   }

   public WasmInstructionBuilder floatBinary(WasmFloatType type, WasmFloatBinaryOperation operation) {
      return this.add(new WasmFloatBinary(type, operation));
   }

   public WasmInstructionBuilder intUnary(WasmIntType type, WasmIntUnaryOperation operation) {
      return this.add(new WasmIntUnary(type, operation));
   }

   public WasmInstructionBuilder floatUnary(WasmFloatType type, WasmFloatUnaryOperation operation) {
      return this.add(new WasmFloatUnary(type, operation));
   }

   public WasmInstructionBuilder convert(WasmNumType sourceType, WasmNumType targetType, boolean signed) {
      return this.add(new WasmConversion(sourceType, targetType, signed));
   }

   public WasmInstructionBuilder reinterpret(WasmNumType sourceType, WasmNumType targetType) {
      WasmConversion insn = new WasmConversion(sourceType, targetType, false);
      insn.setReinterpret(true);
      return this.add(insn);
   }

   public WasmInstructionBuilder nonTrapConvert(WasmNumType sourceType, WasmNumType targetType, boolean signed) {
      WasmConversion insn = new WasmConversion(sourceType, targetType, signed);
      insn.setNonTrapping(true);
      return this.add(insn);
   }

   public WasmInstructionBuilder call(WasmFunction function) {
      return this.call(function, false);
   }

   public WasmInstructionBuilder call(WasmFunction function, boolean suspend) {
      WasmCall insn = new WasmCall(function);
      insn.setSuspend(suspend);
      return this.add(insn);
   }

   public WasmInstructionBuilder callIndirect(WasmFunctionType type) {
      return this.add(new WasmIndirectCall(type));
   }

   public WasmInstructionBuilder callReference(WasmFunctionType type) {
      return this.callReference(type, false);
   }

   public WasmInstructionBuilder callReference(WasmFunctionType type, boolean suspend) {
      WasmCallReference insn = new WasmCallReference(type);
      insn.setSuspend(suspend);
      return this.add(insn);
   }

   public WasmInstructionBuilder drop() {
      if (this.list.getLast() instanceof WasmGetLocal) {
         this.list.getLast().delete();
         this.typeInference.typeStack.remove(this.typeInference.typeStack.size() - 1);
         return this;
      } else if (this.list.getLast() instanceof WasmTeeLocal) {
         WasmTeeLocal teeLocal = (WasmTeeLocal)this.list.getLast();
         WasmSetLocal setLocal = new WasmSetLocal(teeLocal.getLocal());
         setLocal.setLocation(teeLocal.getLocation());
         this.list.getLast().delete();
         this.list.add(setLocal);
         this.typeInference.typeStack.remove(this.typeInference.typeStack.size() - 1);
         return this;
      } else {
         return this.add(new WasmDrop());
      }
   }

   public WasmInstructionBuilder loadI32(int alignment, int offset, WasmInt32Subtype convertFrom) {
      WasmLoadInt32 insn = new WasmLoadInt32(alignment, convertFrom);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder loadI64(int alignment, int offset, WasmInt64Subtype convertFrom) {
      WasmLoadInt64 insn = new WasmLoadInt64(alignment, convertFrom);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder loadF32(int alignment, int offset) {
      WasmLoadFloat32 insn = new WasmLoadFloat32(alignment);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder loadF64(int alignment, int offset) {
      WasmLoadFloat64 insn = new WasmLoadFloat64(alignment);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder storeI32(int alignment, int offset, WasmInt32Subtype convertTo) {
      WasmStoreInt32 insn = new WasmStoreInt32(alignment, convertTo);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder storeI64(int alignment, int offset, WasmInt64Subtype convertTo) {
      WasmStoreInt64 insn = new WasmStoreInt64(alignment, convertTo);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder storeF32(int alignment, int offset) {
      WasmStoreFloat32 insn = new WasmStoreFloat32(alignment);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder storeF64(int alignment, int offset) {
      WasmStoreFloat64 insn = new WasmStoreFloat64(alignment);
      insn.setOffset(offset);
      return this.add(insn);
   }

   public WasmInstructionBuilder memoryGrow() {
      return this.add(new WasmMemoryGrow());
   }

   public WasmInstructionBuilder fill() {
      return this.add(new WasmFill());
   }

   public WasmInstructionBuilder copy() {
      return this.add(new WasmCopy());
   }

   public WasmTry try_(WasmType resultType) {
      WasmTry insn = new WasmTry();
      insn.setType(resultType);
      this.add(insn);
      return insn;
   }

   public WasmTry try_() {
      return this.try_(null);
   }

   public WasmInstructionBuilder throw_(WasmTag tag) {
      return this.add(new WasmThrow(tag));
   }

   public WasmInstructionBuilder refEqual() {
      return this.add(new WasmReferencesEqual());
   }

   public WasmInstructionBuilder cast(WasmType.Reference targetType) {
      return this.add(new WasmCast(targetType));
   }

   public WasmInstructionBuilder cast(WasmCompositeType targetType) {
      return this.cast(targetType.getReference());
   }

   public WasmInstructionBuilder test(WasmType.Reference testType) {
      return this.add(new WasmTest(testType));
   }

   public WasmInstructionBuilder externConvert(WasmExternConversionType type) {
      return this.add(new WasmExternConversion(type));
   }

   public WasmInstructionBuilder structNew(WasmStructure type) {
      return this.add(new WasmStructNew(type));
   }

   public WasmInstructionBuilder structNewDefault(WasmStructure type) {
      this.add(new WasmStructNewDefault(type));
      return this;
   }

   public WasmInstructionBuilder structGet(WasmStructure type, int fieldIndex) {
      return this.add(new WasmStructGet(type, fieldIndex));
   }

   public WasmInstructionBuilder structGet(WasmStructure type, int fieldIndex, WasmSignedType signedType) {
      WasmStructGet insn = new WasmStructGet(type, fieldIndex);
      insn.setSignedType(signedType);
      return this.add(insn);
   }

   public WasmInstructionBuilder structSet(WasmStructure type, int fieldIndex) {
      return this.add(new WasmStructSet(type, fieldIndex));
   }

   public WasmInstructionBuilder arrayNewDefault(WasmArray type) {
      return this.add(new WasmArrayNewDefault(type));
   }

   public WasmInstructionBuilder arrayNewFixed(WasmArray type, int size) {
      return this.add(new WasmArrayNewFixed(type, size));
   }

   public WasmInstructionBuilder arrayGet(WasmArray type) {
      return this.add(new WasmArrayGet(type));
   }

   public WasmInstructionBuilder arrayGet(WasmArray type, WasmSignedType signedType) {
      WasmArrayGet insn = new WasmArrayGet(type);
      insn.setSignedType(signedType);
      return this.add(insn);
   }

   public WasmInstructionBuilder arraySet(WasmArray type) {
      return this.add(new WasmArraySet(type));
   }

   public WasmInstructionBuilder arrayLength() {
      return this.add(new WasmArrayLength());
   }

   public WasmInstructionBuilder arrayCopy(WasmArray targetArrayType, WasmArray sourceArrayType) {
      return this.add(new WasmArrayCopy(targetArrayType, sourceArrayType));
   }

   public WasmInstructionBuilder funcRef(WasmFunction function) {
      return this.add(new WasmFunctionReference(function));
   }

   public WasmInstructionBuilder i31Ref() {
      return this.add(new WasmInt31Reference());
   }

   public WasmInstructionBuilder i31Get(WasmSignedType signedType) {
      return this.add(new WasmInt31Get(signedType));
   }

   public WasmInstructionBuilder add(WasmInstruction instruction) {
      if (!this.isTerminating()) {
         instruction.setLocation(this.currentLocation);
         this.list.add(instruction);
         instruction.acceptVisitor(this.typeInference);
      }

      return this;
   }

   public WasmInstructionBuilder append(WasmFragmentBuilder fragment) {
      fragment.emit(this);
      return this;
   }

   public boolean isTerminating() {
      return this.list.getLast() != null && this.list.getLast().isTerminating();
   }

   public WasmInstructionBuilder transferFrom(WasmInstructionList src) {
      if (this.isTerminating()) {
         return this;
      }

      for (WasmInstruction insn : src) {
         insn.acceptVisitor(this.typeInference);
      }

      this.list.transferFrom(src);
      return this;
   }

   public WasmInstructionBuilder transferFrom(WasmInstructionBuilder src) {
      return this.transferFrom(src.list);
   }

   public WasmInstructionBuilder negate() {
      if (!this.isTerminating()) {
         WasmInstructionUtil.negate(this.list.getLast());
      }

      return this;
   }
}
