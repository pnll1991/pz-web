package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.ConstantExpr;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.WasmGeneratorUtil;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.model.ValueType;

public class StructureIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public StructureIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "toAddress":
         case "cast":
            context.generate(builder, invocation.getArguments().get(0));
            break;
         case "sizeOf": {
            ValueType.Object type = (ValueType.Object)((ConstantExpr)invocation.getArguments().get(0)).getValue();
            builder.i32Const(this.classInfoProvider.getHeapSize(type.getClassName()));
            break;
         }
         case "add": {
            Object type = ((ConstantExpr)invocation.getArguments().get(0)).getValue();
            String className = ((ValueType.Object)type).getClassName();
            int size = this.classInfoProvider.getHeapSize(className);
            int alignment = this.classInfoProvider.getHeapAlignment(className);
            size = WasmGeneratorUtil.align(size, alignment);
            context.generate(builder, invocation.getArguments().get(1));
            context.generate(builder, invocation.getArguments().get(2));
            builder.i32Const(size).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.MUL).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD);
            break;
         }
         default:
            throw new IllegalArgumentException(invocation.getMethod().toString());
      }
   }
}
