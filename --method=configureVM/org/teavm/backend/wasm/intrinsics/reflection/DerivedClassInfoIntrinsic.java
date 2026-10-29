package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.DerivedClassInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;

public class DerivedClassInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public DerivedClassInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      DerivedClassInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().derivedClassInfo();
      context.generate(builder, invocation.getArguments().get(0));
      switch (invocation.getMethod().getName()) {
         case "classInfo":
            builder.structGet(infoStruct.structure(), infoStruct.classInfoIndex());
            break;
         case "arrayDegree":
            builder.structGet(infoStruct.structure(), infoStruct.arrayDegreeIndex(), WasmSignedType.SIGNED);
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
