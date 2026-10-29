package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.ReflectionTypes;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.GenericTypeInfo;

public class GenericTypeInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;
   private final WasmFunctionTypes functionTypes;
   private final WasmGCNameProvider names;
   private final WasmModule module;
   private WasmFunction kindFunction;

   public GenericTypeInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider, WasmFunctionTypes functionTypes, WasmGCNameProvider names, WasmModule module) {
      this.classInfoProvider = classInfoProvider;
      this.functionTypes = functionTypes;
      this.names = names;
      this.module = module;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      ReflectionTypes reflectionTypes = this.classInfoProvider.reflectionTypes();
      switch (invocation.getMethod().getName()) {
         case "kind":
            context.generate(builder, invocation.getArguments().get(0));
            builder.call(this.getKindFunction());
            break;
         case "asParameterizedType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.cast(reflectionTypes.parameterizedTypeInfo().structure().getReference());
            break;
         case "asTypeVariable":
            context.generate(builder, invocation.getArguments().get(0));
            builder.cast(reflectionTypes.typeVariableReference().structure().getReference());
            break;
         case "asGenericArray":
            context.generate(builder, invocation.getArguments().get(0));
            builder.cast(reflectionTypes.genericArrayInfo().structure().getReference());
            break;
         case "asWildcard":
            context.generate(builder, invocation.getArguments().get(0));
            builder.cast(reflectionTypes.wildcardTypeInfo().structure().getReference());
            break;
         case "asRawType":
            context.generate(builder, invocation.getArguments().get(0));
            builder.cast(reflectionTypes.derivedClassInfo().structure().getReference());
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }

   private WasmFunction getKindFunction() {
      if (this.kindFunction == null) {
         this.kindFunction = new WasmFunction(this.functionTypes.of(WasmType.INT32, WasmType.STRUCT));
         this.kindFunction.setName(this.names.topLevel(this.names.suggestForMethod(new MethodReference(GenericTypeInfo.class, "kind", int.class))));
         ReflectionTypes reflectionTypes = this.classInfoProvider.reflectionTypes();
         WasmLocal param = new WasmLocal(WasmType.STRUCT, "this");
         this.kindFunction.add(param);
         WasmInstructionBuilder body = this.kindFunction.getBody().builder();
         body.getLocal(param).test(reflectionTypes.parameterizedTypeInfo().structure().getReference());
         body.conditional().getThenBlock().builder().i32Const(0).return_();
         body.getLocal(param).test(reflectionTypes.typeVariableReference().structure().getReference());
         body.conditional().getThenBlock().builder().i32Const(1).return_();
         body.getLocal(param).test(reflectionTypes.genericArrayInfo().structure().getReference());
         body.conditional().getThenBlock().builder().i32Const(2).return_();
         WasmStructure wildcardStruct = reflectionTypes.wildcardTypeInfo().structure();
         body.getLocal(param).test(wildcardStruct.getReference());
         body.conditional()
            .getThenBlock()
            .builder()
            .getLocal(param)
            .cast(wildcardStruct.getReference())
            .structGet(wildcardStruct, reflectionTypes.wildcardTypeInfo().kindIndex())
            .i32Const(3)
            .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD)
            .return_();
         body.i32Const(6);
         this.module.functions.add(this.kindFunction);
      }

      return this.kindFunction;
   }
}
