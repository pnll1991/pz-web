package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.model.MethodReference;

public class StringIntrinsic implements WasmGCInlineIntrinsic {
   private WasmGCCodeGenContext codeGenContext;

   public StringIntrinsic(WasmGCCodeGenContext codeGenContext) {
      this.codeGenContext = codeGenContext;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmFunction worker = this.codeGenContext.functions().forStaticMethod(new MethodReference(StringInternPool.class, "query", String.class, String.class));
      context.generate(builder, invocation.getArguments().get(0));
      builder.call(worker);
   }
}
