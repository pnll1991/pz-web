package org.teavm.backend.wasm.intrinsics.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.generate.reflection.MethodInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.model.MethodReference;

public class MethodInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public MethodInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      MethodInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().methodInfo();
      switch (invocation.getMethod().getName()) {
         case "name":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.nameIndex());
            break;
         case "modifiers":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.modifiersIndex());
            break;
         case "returnType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.returnTypeIndex());
            break;
         case "parameterCount":
            WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
               context.generate(b, invocation.getArguments().get(0));
               b.structGet(infoStruct.structure(), infoStruct.parameterTypesIndex());
            });
            break;
         case "parameterType":
            WasmArray paramsType = this.classInfoProvider.reflectionTypes().derivedClassInfo().array();
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.parameterTypesIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(paramsType);
            break;
         case "checkedExceptionCount":
            WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
               context.generate(b, invocation.getArguments().get(0));
               b.structGet(infoStruct.structure(), infoStruct.checkedExceptionTypesIndex());
            });
            break;
         case "checkedExceptionType":
            WasmArray exceptionTypesArray = this.classInfoProvider.reflectionTypes().classInfo().array();
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.checkedExceptionTypesIndex());
            context.generate(builder, invocation.getArguments().get(1));
            builder.arrayGet(exceptionTypesArray);
            break;
         case "call":
            boolean isAsync = context.isAsyncMethod(new MethodReference(Method.class, "invoke", Object.class, Object[].class, Object.class));
            isAsync |= context.isAsyncMethod(new MethodReference(Constructor.class, "newInstance", Object[].class, Object.class));
            context.generate(builder, invocation.getArguments().get(1));
            context.generate(builder, invocation.getArguments().get(2));
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.callerIndex()).callReference(infoStruct.callerType(), isAsync);
            break;
         case "reflection":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.reflectionIndex());
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
