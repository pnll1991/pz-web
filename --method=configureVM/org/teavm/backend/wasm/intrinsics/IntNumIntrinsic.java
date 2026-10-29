package org.teavm.backend.wasm.intrinsics;

import java.util.Set;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmRuntime;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmIntUnaryOperation;
import org.teavm.model.MethodReference;

public class IntNumIntrinsic implements WasmGCInlineIntrinsic {
   static final Set<String> METHODS = Set.of(
      "divideUnsigned", "remainderUnsigned", "compareUnsigned", "numberOfLeadingZeros", "numberOfTrailingZeros", "bitCount"
   );
   private final MethodReference compareUnsigned;
   private final WasmIntType wasmType;
   private final BaseWasmFunctionRepository functions;

   public IntNumIntrinsic(Class<?> javaType, WasmIntType wasmType, BaseWasmFunctionRepository functions) {
      this.compareUnsigned = new MethodReference(WasmRuntime.class, "compareUnsigned", javaType, javaType, int.class);
      this.wasmType = wasmType;
      this.functions = functions;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "divideUnsigned":
            context.generate(builder, invocation.getArguments().get(0));
            context.generate(builder, invocation.getArguments().get(1));
            builder.intBinary(this.wasmType, WasmIntBinaryOperation.DIV_UNSIGNED);
            break;
         case "remainderUnsigned":
            context.generate(builder, invocation.getArguments().get(0));
            context.generate(builder, invocation.getArguments().get(1));
            builder.intBinary(this.wasmType, WasmIntBinaryOperation.REM_UNSIGNED);
            break;
         case "compareUnsigned":
            context.generate(builder, invocation.getArguments().get(0));
            context.generate(builder, invocation.getArguments().get(1));
            builder.call(this.functions.forStaticMethod(this.compareUnsigned));
            break;
         case "numberOfLeadingZeros":
            context.generate(builder, invocation.getArguments().get(0));
            builder.intUnary(this.wasmType, WasmIntUnaryOperation.CLZ);
            this.castToInt(builder);
            break;
         case "numberOfTrailingZeros":
            context.generate(builder, invocation.getArguments().get(0));
            builder.intUnary(this.wasmType, WasmIntUnaryOperation.CTZ);
            this.castToInt(builder);
            break;
         case "bitCount":
            context.generate(builder, invocation.getArguments().get(0));
            builder.intUnary(this.wasmType, WasmIntUnaryOperation.POPCNT);
            this.castToInt(builder);
            break;
         default:
            throw new AssertionError();
      }
   }

   private void castToInt(WasmInstructionBuilder builder) {
      if (this.wasmType == WasmIntType.INT64) {
         builder.convert(WasmNumType.INT64, WasmNumType.INT32, false);
      }
   }
}
