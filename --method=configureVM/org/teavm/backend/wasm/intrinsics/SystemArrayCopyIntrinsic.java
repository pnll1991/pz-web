package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.Expr;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.CachedValue;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.runtime.WasmGCSupport;
import org.teavm.backend.wasm.types.PreciseValueType;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class SystemArrayCopyIntrinsic implements WasmGCInlineIntrinsic {
   private ClassHierarchy hierarchy;
   private WasmModule module;
   private BaseWasmFunctionRepository functions;
   private WasmGCClassInfoProvider classInfoProvider;
   private WasmGCTypeMapper typeMapper;
   private WasmFunctionTypes functionTypes;
   private WasmGCNameProvider names;
   private WasmTag exceptionTag;
   private WasmFunction defaultFunction;
   private WasmFunction argsCheckFunction;

   public SystemArrayCopyIntrinsic(
      ClassHierarchy hierarchy,
      WasmModule module,
      BaseWasmFunctionRepository functions,
      WasmGCClassInfoProvider classInfoProvider,
      WasmGCTypeMapper typeMapper,
      WasmFunctionTypes functionTypes,
      WasmGCNameProvider names,
      WasmTag exceptionTag
   ) {
      this.hierarchy = hierarchy;
      this.module = module;
      this.functions = functions;
      this.classInfoProvider = classInfoProvider;
      this.typeMapper = typeMapper;
      this.functionTypes = functionTypes;
      this.names = names;
      this.exceptionTag = exceptionTag;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "arraycopy":
            this.generateArrayCopy(invocation, context, builder);
            break;
         case "doArrayCopy":
            this.generateDoArrayCopy(invocation, context, builder);
            break;
         default:
            throw new IllegalArgumentException();
      }
   }

   private void generateArrayCopy(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      if (!this.tryGenerateSpecialCase(invocation, context, builder)) {
         for (int i = 0; i < 5; i++) {
            context.generate(builder, invocation.getArguments().get(i));
         }

         builder.call(this.getDefaultFunction());
      }
   }

   private void generateDoArrayCopy(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      ClassInfoStruct classInfoStruct = this.classInfoProvider.reflectionTypes().classInfo();
      WasmGCClassInfo objInfo = this.classInfoProvider.getClassInfo(Object.class.getName());
      context.generate(builder, invocation.getArguments().get(0));
      CachedValue source = context.valueCache().create(objInfo.getType(), builder);
      builder.structGet(objInfo.getStructure(), 0).structGet(objInfo.getVirtualTableStructure(), 0);
      CachedValue sourceClsCached = context.valueCache().create(classInfoStruct.structure().getReference(), builder);
      builder.append(source);
      context.generate(builder, invocation.getArguments().get(1));
      context.generate(builder, invocation.getArguments().get(2));
      context.generate(builder, invocation.getArguments().get(3));
      context.generate(builder, invocation.getArguments().get(4));
      builder.append(sourceClsCached)
         .structGet(classInfoStruct.structure(), classInfoStruct.copyArrayIndex())
         .callReference(classInfoStruct.copyArrayFunctionType());
      source.release();
      sourceClsCached.release();
   }

   private boolean tryGenerateSpecialCase(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      Expr sourceArray = invocation.getArguments().get(0);
      Expr targetArray = invocation.getArguments().get(2);
      if (sourceArray.getVariableIndex() >= 0 && targetArray.getVariableIndex() >= 0) {
         PreciseValueType sourceType = context.types().typeOf(sourceArray.getVariableIndex());
         if (sourceType != null && sourceType.valueType instanceof ValueType.Array) {
            PreciseValueType targetType = context.types().typeOf(targetArray.getVariableIndex());
            if (targetType != null && targetType.valueType instanceof ValueType.Array) {
               ValueType sourceItemType = ((ValueType.Array)sourceType.valueType).getItemType();
               ValueType targetItemType = ((ValueType.Array)targetType.valueType).getItemType();
               if (sourceItemType == targetItemType && this.hierarchy.isSuperType(targetItemType, sourceItemType, false)) {
                  WasmType.CompositeReference wasmTargetArrayType = (WasmType.CompositeReference)this.typeMapper.mapType(ValueType.arrayOf(targetItemType));
                  WasmStructure wasmTargetArrayStruct = (WasmStructure)wasmTargetArrayType.composite;
                  WasmType.CompositeReference wasmTargetArrayTypeRef = (WasmType.CompositeReference)wasmTargetArrayStruct.getFields().get(2).getUnpackedType();
                  if (context.isAsync()) {
                     wasmTargetArrayTypeRef = wasmTargetArrayTypeRef.composite.getReference();
                  }

                  context.generate(builder, invocation.getArguments().get(2));
                  builder.structGet(wasmTargetArrayStruct, 2);
                  CachedValue wasmTargetArray = context.valueCache().create(wasmTargetArrayTypeRef, builder);
                  builder.drop();
                  context.generate(builder, invocation.getArguments().get(3));
                  CachedValue wasmTargetIndex = context.valueCache().create(WasmType.INT32, builder);
                  builder.drop();
                  WasmType.CompositeReference wasmSourceArrayType = (WasmType.CompositeReference)this.typeMapper.mapType(ValueType.arrayOf(sourceItemType));
                  WasmStructure wasmSourceArrayStruct = (WasmStructure)wasmSourceArrayType.composite;
                  WasmType.CompositeReference wasmSourceArrayTypeRef = (WasmType.CompositeReference)wasmSourceArrayStruct.getFields().get(2).getUnpackedType();
                  if (context.isAsync()) {
                     wasmSourceArrayTypeRef = wasmSourceArrayTypeRef.composite.getReference();
                  }

                  context.generate(builder, invocation.getArguments().get(0));
                  builder.structGet(wasmSourceArrayStruct, 2);
                  CachedValue wasmSourceArray = context.valueCache().create(wasmSourceArrayTypeRef, builder);
                  builder.drop();
                  context.generate(builder, invocation.getArguments().get(1));
                  CachedValue wasmSourceIndex = context.valueCache().create(WasmType.INT32, builder);
                  builder.drop();
                  context.generate(builder, invocation.getArguments().get(4));
                  CachedValue wasmSize = context.valueCache().create(WasmType.INT32, builder);
                  builder.drop();
                  builder.append(wasmTargetArray).append(wasmTargetIndex).append(wasmSourceArray).append(wasmSourceIndex).append(wasmSize);
                  builder.call(this.getArgsCheckFunction());
                  builder.append(wasmTargetArray).append(wasmTargetIndex).append(wasmSourceArray).append(wasmSourceIndex).append(wasmSize);
                  builder.arrayCopy((WasmArray)wasmTargetArrayTypeRef.composite, (WasmArray)wasmSourceArrayTypeRef.composite);
                  wasmTargetArray.release();
                  wasmTargetIndex.release();
                  wasmSourceArray.release();
                  wasmSourceIndex.release();
                  wasmSize.release();
                  return true;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private WasmFunction getArgsCheckFunction() {
      if (this.argsCheckFunction == null) {
         this.argsCheckFunction = this.createArgsCheckFunction();
      }

      return this.argsCheckFunction;
   }

   private WasmFunction createArgsCheckFunction() {
      WasmFunction function = new WasmFunction(this.functionTypes.of(null, WasmType.ARRAY, WasmType.INT32, WasmType.ARRAY, WasmType.INT32, WasmType.INT32));
      function.setName(this.names.topLevel("teavm@checkArrayCopy"));
      this.module.functions.add(function);
      WasmLocal targetArrayLocal = new WasmLocal(WasmType.ARRAY, "targetArray");
      WasmLocal targetArrayIndexLocal = new WasmLocal(WasmType.INT32, "targetIndex");
      WasmLocal sourceArrayLocal = new WasmLocal(WasmType.ARRAY, "sourceArray");
      WasmLocal sourceArrayIndexLocal = new WasmLocal(WasmType.INT32, "sourceIndex");
      WasmLocal countLocal = new WasmLocal(WasmType.INT32, "count");
      function.add(targetArrayLocal);
      function.add(targetArrayIndexLocal);
      function.add(sourceArrayLocal);
      function.add(sourceArrayIndexLocal);
      function.add(countLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      WasmInstructionBuilder blockBody = body.block();
      blockBody.getLocal(targetArrayIndexLocal).i32Const(0).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED).branch(blockBody);
      blockBody.getLocal(sourceArrayIndexLocal).i32Const(0).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED).branch(blockBody);
      blockBody.getLocal(countLocal).i32Const(0).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED).branch(blockBody);
      blockBody.getLocal(targetArrayIndexLocal)
         .getLocal(targetArrayLocal)
         .arrayLength()
         .getLocal(countLocal)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GT_SIGNED)
         .branch(blockBody);
      blockBody.getLocal(sourceArrayIndexLocal)
         .getLocal(sourceArrayLocal)
         .arrayLength()
         .getLocal(countLocal)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB)
         .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GT_SIGNED)
         .branch(blockBody);
      blockBody.return_();
      WasmFunction aioobeFunction = this.functions.forStaticMethod(new MethodReference(WasmGCSupport.class, "aiiobe", ArrayIndexOutOfBoundsException.class));
      body.call(aioobeFunction).throw_(this.exceptionTag);
      return function;
   }

   private WasmFunction getDefaultFunction() {
      if (this.defaultFunction == null) {
         this.defaultFunction = this.functions
            .forStaticMethod(new MethodReference(System.class, "arrayCopyImpl", Object.class, int.class, Object.class, int.class, int.class, void.class));
      }

      return this.defaultFunction;
   }
}
