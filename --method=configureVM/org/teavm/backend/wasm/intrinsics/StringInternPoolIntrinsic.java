package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.CachedValue;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.model.ValueType;

class StringInternPoolIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;
   private final WasmFunctionTypes functionTypes;
   private final WasmGCTypeMapper typeMapper;
   private final WasmGCNameProvider names;
   private final WasmModule module;

   StringInternPoolIntrinsic(
      WasmGCClassInfoProvider classInfoProvider, WasmFunctionTypes functionTypes, WasmGCTypeMapper typeMapper, WasmGCNameProvider names, WasmModule module
   ) {
      this.classInfoProvider = classInfoProvider;
      this.functionTypes = functionTypes;
      this.typeMapper = typeMapper;
      this.names = names;
      this.module = module;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmStructure entryStruct = this.classInfoProvider.getClassInfo(StringInternPool.class.getName() + "$Entry").getStructure();
      switch (invocation.getMethod().getName()) {
         case "getValue":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(entryStruct, 5);
            builder.call(this.createDerefFunction());
            break;
         case "setValue":
            context.generate(builder, invocation.getArguments().get(0));
            CachedValue instance = context.valueCache().create(entryStruct.getReference(), builder);
            builder.drop();
            builder.append(instance);
            context.generate(builder, invocation.getArguments().get(1));
            builder.append(instance);
            builder.call(this.createRefFunction());
            builder.structSet(entryStruct, 5);
            instance.release();
            break;
         default:
            throw new IllegalArgumentException();
      }
   }

   private WasmFunction createRefFunction() {
      WasmFunction function = new WasmFunction(
         this.functionTypes
            .of(
               WasmType.EXTERN,
               this.typeMapper.mapType(ValueType.parse(String.class)),
               this.typeMapper.mapType(ValueType.object(StringInternPool.class.getName() + "$Entry"))
            )
      );
      function.setName(this.names.topLevel("teavm@stringRef"));
      function.setImportModule("teavm");
      function.setImportName("createStringWeakRef");
      this.module.functions.add(function);
      return function;
   }

   private WasmFunction createDerefFunction() {
      WasmFunction function = new WasmFunction(this.functionTypes.of(this.typeMapper.mapType(ValueType.parse(String.class)), WasmType.EXTERN));
      function.setName(this.names.topLevel("teavm@stringDeref"));
      function.setImportModule("teavm");
      function.setImportName("stringDeref");
      this.module.functions.add(function);
      return function;
   }
}
