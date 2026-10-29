package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.WasmGeneratorUtil;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmFloatBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmFloatType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;

public class WasmRuntimeIntrinsic implements WasmGCInlineIntrinsic {
   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "lt":
            comparison(WasmIntBinaryOperation.LT_SIGNED, WasmFloatBinaryOperation.LT, invocation, context, builder);
            break;
         case "gt":
            comparison(WasmIntBinaryOperation.GT_SIGNED, WasmFloatBinaryOperation.GT, invocation, context, builder);
            break;
         case "ltu":
            comparison(WasmIntBinaryOperation.LT_UNSIGNED, WasmFloatBinaryOperation.LT, invocation, context, builder);
            break;
         case "gtu":
            comparison(WasmIntBinaryOperation.GT_UNSIGNED, WasmFloatBinaryOperation.GT, invocation, context, builder);
            break;
         case "min":
            comparison(WasmIntBinaryOperation.GT_SIGNED, WasmFloatBinaryOperation.MIN, invocation, context, builder);
            break;
         case "max":
            comparison(WasmIntBinaryOperation.GT_SIGNED, WasmFloatBinaryOperation.MAX, invocation, context, builder);
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }

   private static void comparison(
      WasmIntBinaryOperation intOp,
      WasmFloatBinaryOperation floatOp,
      InvocationExpr invocation,
      WasmGCInlineIntrinsicContext context,
      WasmInstructionBuilder builder
   ) {
      WasmType.Number type = (WasmType.Number)WasmGeneratorUtil.mapType(invocation.getMethod().parameterType(0));
      context.generate(builder, invocation.getArguments().get(0));
      context.generate(builder, invocation.getArguments().get(1));
      switch (type.number) {
         case INT32:
            builder.intBinary(WasmIntType.INT32, intOp);
            break;
         case INT64:
            builder.intBinary(WasmIntType.INT64, intOp);
            break;
         case FLOAT32:
            builder.floatBinary(WasmFloatType.FLOAT32, floatOp);
            break;
         case FLOAT64:
            builder.floatBinary(WasmFloatType.FLOAT64, floatOp);
            break;
         default:
            throw new IllegalArgumentException(type.toString());
      }
   }
}
