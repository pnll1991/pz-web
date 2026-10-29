package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.ParameterizedTypeInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ReflectionTypes;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class ParameterizedTypeInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public ParameterizedTypeInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      ReflectionTypes reflectionTypes = this.classInfoProvider.reflectionTypes();
      ParameterizedTypeInfoStruct paramType = reflectionTypes.parameterizedTypeInfo();
      switch (invocation.getMethod().getName()) {
         case "rawType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(paramType.structure(), paramType.rawTypeIndex());
            break;
         case "actualTypeArgumentCount":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(paramType.structure(), paramType.actualTypeArgumentsIndex()).arrayLength();
            break;
         case "actualTypeArgument":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(paramType.structure(), paramType.actualTypeArgumentsIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(reflectionTypes.genericTypeArray());
            break;
         case "ownerType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(paramType.structure(), paramType.ownerTypeIndex());
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
