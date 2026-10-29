package org.teavm.backend.wasm.model.instruction;

public final class WasmInstructionUtil {
   private WasmInstructionUtil() {
   }

   public static void negate(WasmInstruction instruction) {
      if (instruction instanceof WasmIntBinary binary) {
         WasmIntBinaryOperation negatedOp = negate(binary.getOperation());
         if (negatedOp != null) {
            binary.setOperation(negatedOp);
            return;
         }
      } else if (instruction instanceof WasmFloatBinary binary) {
         WasmFloatBinaryOperation negatedOp = negate(binary.getOperation());
         if (negatedOp != null) {
            binary.setOperation(negatedOp);
            return;
         }
      } else if (instruction instanceof WasmIntUnary unary) {
         if (unary.getOperation() == WasmIntUnaryOperation.EQZ) {
            unary.delete();
            return;
         }
      } else if (instruction instanceof WasmInt32Constant cst) {
         if (cst.getValue() == 0) {
            cst.setValue(1);
         } else {
            cst.setValue(0);
         }

         return;
      }

      WasmIntUnary newInsn = new WasmIntUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ);
      newInsn.setLocation(instruction.getLocation());
      instruction.insertNext(newInsn);
   }

   private static WasmIntBinaryOperation negate(WasmIntBinaryOperation op) {
      switch (op) {
         case EQ:
            return WasmIntBinaryOperation.NE;
         case NE:
            return WasmIntBinaryOperation.EQ;
         case LT_SIGNED:
            return WasmIntBinaryOperation.GE_SIGNED;
         case LT_UNSIGNED:
            return WasmIntBinaryOperation.GE_UNSIGNED;
         case LE_SIGNED:
            return WasmIntBinaryOperation.GT_SIGNED;
         case LE_UNSIGNED:
            return WasmIntBinaryOperation.GT_UNSIGNED;
         case GT_SIGNED:
            return WasmIntBinaryOperation.LE_SIGNED;
         case GT_UNSIGNED:
            return WasmIntBinaryOperation.LE_UNSIGNED;
         case GE_SIGNED:
            return WasmIntBinaryOperation.LT_SIGNED;
         case GE_UNSIGNED:
            return WasmIntBinaryOperation.LT_UNSIGNED;
         default:
            return null;
      }
   }

   private static WasmFloatBinaryOperation negate(WasmFloatBinaryOperation op) {
      switch (op) {
         case EQ:
            return WasmFloatBinaryOperation.NE;
         case NE:
            return WasmFloatBinaryOperation.EQ;
         default:
            return null;
      }
   }
}
