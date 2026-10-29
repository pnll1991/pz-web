package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.CachedValue;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmFloatBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmFloatType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;

public class FloatIntrinsic implements WasmGCInlineIntrinsic {
   private static final int EXPONENT_BITS = 2139095040;

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "getNaN":
            builder.f32Const(Float.NaN);
            break;
         case "isNaN":
            context.generate(builder, invocation.getArguments().get(0));
            CachedValue cache = context.valueCache().create(WasmType.FLOAT32, builder);
            builder.drop();
            builder.append(cache).append(cache).floatBinary(WasmFloatType.FLOAT32, WasmFloatBinaryOperation.NE);
            cache.release();
            break;
         case "isFinite":
            context.generate(builder, invocation.getArguments().get(0));
            builder.reinterpret(WasmNumType.FLOAT32, WasmNumType.INT32);
            builder.i32Const(2139095040)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.AND)
               .i32Const(2139095040)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.NE);
            break;
         case "floatToRawIntBits":
            context.generate(builder, invocation.getArguments().get(0));
            builder.reinterpret(WasmNumType.FLOAT32, WasmNumType.INT32);
            break;
         case "intBitsToFloat":
            context.generate(builder, invocation.getArguments().get(0));
            builder.reinterpret(WasmNumType.INT32, WasmNumType.FLOAT32);
            break;
         default:
            throw new AssertionError();
      }
   }
}
