package org.teavm.backend.wasm.model.instruction;

import java.util.ArrayList;
import java.util.List;
import org.teavm.backend.wasm.model.WasmBlockType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmType;

public class WasmTypeInference implements WasmInstructionVisitor {
   public final List<WasmType> typeStack = new ArrayList<>();
   private final boolean forceNullable;
   private int depthBeforeLastInstructionOut;

   public WasmTypeInference() {
      this(false);
   }

   public WasmTypeInference(boolean forceNullable) {
      this.forceNullable = forceNullable;
   }

   public int getDepthBeforeLastInstructionOut() {
      return this.depthBeforeLastInstructionOut;
   }

   @Override
   public void visit(WasmUnreachable instruction) {
      this.typeStack.clear();
      this.depthBeforeLastInstructionOut = 0;
   }

   @Override
   public void visit(WasmBlock instruction) {
      WasmBlockType type = instruction.getType();
      if (type != null) {
         this.popN(type.getInputTypes().size());
         this.depthBeforeLastInstructionOut = this.typeStack.size();
         this.typeStack.addAll(type.getOutputTypes());
      } else {
         this.depthBeforeLastInstructionOut = this.typeStack.size();
      }
   }

   @Override
   public void visit(WasmConditional instruction) {
      this.pop();
      WasmBlockType type = instruction.getType();
      if (type != null) {
         this.popN(type.getInputTypes().size());
         this.depthBeforeLastInstructionOut = this.typeStack.size();
         this.typeStack.addAll(type.getOutputTypes());
      } else {
         this.depthBeforeLastInstructionOut = this.typeStack.size();
      }
   }

   @Override
   public void visit(WasmBranch instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmNullBranch instruction) {
      if (instruction.getCondition() == WasmNullCondition.NOT_NULL) {
         this.pop();
         this.depthBeforeLastInstructionOut = this.typeStack.size();
      } else if (!this.typeStack.isEmpty()) {
         this.depthBeforeLastInstructionOut = this.typeStack.size() - 1;
         WasmType top = this.typeStack.get(this.typeStack.size() - 1);
         WasmType.Reference ref = (WasmType.Reference)top;
         if (ref.isNullable()) {
            this.typeStack.set(this.typeStack.size() - 1, ref.asNonNull());
         }
      }
   }

   @Override
   public void visit(WasmCastBranch instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      if (instruction.getCondition() == WasmCastCondition.SUCCESS) {
         this.typeStack.add(instruction.getSourceType());
      } else {
         this.typeStack.add(instruction.getTargetType());
      }
   }

   @Override
   public void visit(WasmBreak instruction) {
      this.typeStack.clear();
      this.depthBeforeLastInstructionOut = 0;
   }

   @Override
   public void visit(WasmSwitch instruction) {
      this.typeStack.clear();
      this.depthBeforeLastInstructionOut = 0;
   }

   @Override
   public void visit(WasmReturn instruction) {
      this.typeStack.clear();
      this.depthBeforeLastInstructionOut = 0;
   }

   @Override
   public void visit(WasmInt32Constant instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   @Override
   public void visit(WasmInt64Constant instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT64);
   }

   @Override
   public void visit(WasmFloat32Constant instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.FLOAT32);
   }

   @Override
   public void visit(WasmFloat64Constant instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.FLOAT64);
   }

   @Override
   public void visit(WasmNullConstant instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getType());
   }

   @Override
   public void visit(WasmIsNull instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   @Override
   public void visit(WasmGetLocal instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getLocal().getType());
   }

   @Override
   public void visit(WasmSetLocal instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmTeeLocal instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getLocal().getType());
   }

   @Override
   public void visit(WasmGetGlobal instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(this.mapToNullable(instruction.getGlobal().getType()));
   }

   @Override
   public void visit(WasmSetGlobal instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmIntBinary instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      switch (instruction.getOperation()) {
         case EQ:
         case NE:
         case LT_SIGNED:
         case LT_UNSIGNED:
         case LE_SIGNED:
         case LE_UNSIGNED:
         case GT_SIGNED:
         case GT_UNSIGNED:
         case GE_SIGNED:
         case GE_UNSIGNED:
            this.typeStack.add(WasmType.INT32);
            break;
         default:
            this.typeStack.add(instruction.getType() == WasmIntType.INT32 ? WasmType.INT32 : WasmType.INT64);
      }
   }

   @Override
   public void visit(WasmFloatBinary instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      switch (instruction.getOperation()) {
         case EQ:
         case NE:
         case LT:
         case LE:
         case GT:
         case GE:
            this.typeStack.add(WasmType.INT32);
            break;
         default:
            this.typeStack.add(instruction.getType() == WasmFloatType.FLOAT32 ? WasmType.FLOAT32 : WasmType.FLOAT64);
      }
   }

   @Override
   public void visit(WasmIntUnary instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      if (instruction.getOperation() == WasmIntUnaryOperation.EQZ) {
         this.typeStack.add(WasmType.INT32);
      } else {
         this.typeStack.add(instruction.getType() == WasmIntType.INT32 ? WasmType.INT32 : WasmType.INT64);
      }
   }

   @Override
   public void visit(WasmFloatUnary instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getType() == WasmFloatType.FLOAT32 ? WasmType.FLOAT32 : WasmType.FLOAT64);
   }

   @Override
   public void visit(WasmConversion instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.num(instruction.getTargetType()));
   }

   @Override
   public void visit(WasmCall instruction) {
      WasmFunction function = instruction.getFunction();
      this.popN(function.getType().getParameterTypes().size());
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.addAll(function.getType().getReturnTypes());
   }

   @Override
   public void visit(WasmIndirectCall instruction) {
      this.popN(instruction.getType().getParameterTypes().size() + 1);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.addAll(instruction.getType().getReturnTypes());
   }

   @Override
   public void visit(WasmCallReference instruction) {
      this.popN(instruction.getType().getParameterTypes().size() + 1);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.addAll(instruction.getType().getReturnTypes());
   }

   @Override
   public void visit(WasmDrop instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmLoadInt32 instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   @Override
   public void visit(WasmLoadInt64 instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT64);
   }

   @Override
   public void visit(WasmLoadFloat32 instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.FLOAT32);
   }

   @Override
   public void visit(WasmLoadFloat64 instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.FLOAT64);
   }

   @Override
   public void visit(WasmStoreInt32 instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmStoreInt64 instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmStoreFloat32 instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmStoreFloat64 instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmMemoryGrow instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   @Override
   public void visit(WasmFill instruction) {
      this.popN(3);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmCopy instruction) {
      this.popN(3);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmTry instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      if (instruction.getType() != null) {
         this.typeStack.add(instruction.getType());
      }
   }

   @Override
   public void visit(WasmThrow instruction) {
      this.typeStack.clear();
      this.depthBeforeLastInstructionOut = 0;
   }

   @Override
   public void visit(WasmReferencesEqual instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   @Override
   public void visit(WasmCast instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getTargetType());
   }

   @Override
   public void visit(WasmTest instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   @Override
   public void visit(WasmExternConversion instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      switch (instruction.getType()) {
         case EXTERN_TO_ANY:
            this.typeStack.add(WasmType.ANY);
            break;
         case ANY_TO_EXTERN:
            this.typeStack.add(WasmType.EXTERN);
      }
   }

   @Override
   public void visit(WasmStructNew instruction) {
      this.popN(instruction.getType().getFields().size());
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(this.mapToNullable(instruction.getType().getReference()));
   }

   @Override
   public void visit(WasmStructNewDefault instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(this.mapToNullable(instruction.getType().getReference()));
   }

   @Override
   public void visit(WasmStructGet instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(this.mapToNullable(instruction.getType().getFields().get(instruction.getFieldIndex()).getUnpackedType()));
   }

   @Override
   public void visit(WasmStructSet instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmArrayNewDefault instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getType().getReference());
   }

   @Override
   public void visit(WasmArrayNewFixed instruction) {
      this.popN(instruction.getSize());
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getType().getReference());
   }

   @Override
   public void visit(WasmArrayGet instruction) {
      this.popN(2);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getType().getElementType().asUnpackedType());
   }

   @Override
   public void visit(WasmArraySet instruction) {
      this.popN(3);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmArrayLength instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   @Override
   public void visit(WasmArrayCopy instruction) {
      this.popN(5);
      this.depthBeforeLastInstructionOut = this.typeStack.size();
   }

   @Override
   public void visit(WasmFunctionReference instruction) {
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(instruction.getFunction().getType().getReference());
   }

   @Override
   public void visit(WasmInt31Reference instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.I31);
   }

   @Override
   public void visit(WasmInt31Get instruction) {
      this.pop();
      this.depthBeforeLastInstructionOut = this.typeStack.size();
      this.typeStack.add(WasmType.INT32);
   }

   private void pop() {
      this.typeStack.remove(this.typeStack.size() - 1);
   }

   private void popN(int count) {
      int removeFrom = this.typeStack.size() - count;
      this.typeStack.subList(removeFrom, this.typeStack.size()).clear();
   }

   private WasmType mapToNullable(WasmType type) {
      if (!this.forceNullable) {
         return type;
      }

      if (type instanceof WasmType.Reference) {
         type = ((WasmType.Reference)type).asNullable();
      }

      return type;
   }
}
