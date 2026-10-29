package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.generate.reflection.MethodReflectionInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ParameterInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class MethodReflectionInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public MethodReflectionInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      MethodReflectionInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().methodReflectionInfo();
      switch (invocation.getMethod().getName()) {
         case "genericReturnType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.genericReturnTypeIndex());
            break;
         case "parameterInfoCount":
            WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
               context.generate(b, invocation.getArguments().get(0));
               b.structGet(infoStruct.structure(), infoStruct.parameterInfosIndex());
            });
            break;
         case "parameterInfo":
            ParameterInfoStruct paramInfoStruct = this.classInfoProvider.reflectionTypes().parameterInfo();
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.parameterInfosIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(paramInfoStruct.array());
            break;
         case "annotationCount":
            WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
               context.generate(b, invocation.getArguments().get(0));
               b.structGet(infoStruct.structure(), infoStruct.annotationsIndex());
            });
            break;
         case "annotation": {
            WasmArray array = this.classInfoProvider.reflectionTypes().annotationInfo().array();
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.annotationsIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(array);
            break;
         }
         case "typeParameterCount":
            WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
               context.generate(b, invocation.getArguments().get(0));
               b.structGet(infoStruct.structure(), infoStruct.typeParametersIndex());
            });
            break;
         case "typeParameter": {
            WasmArray array = this.classInfoProvider.reflectionTypes().typeVariableInfo().array();
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.typeParametersIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(array);
            break;
         }
         default:
            throw new IllegalStateException(invocation.getMethod().getName());
      }
   }
}
