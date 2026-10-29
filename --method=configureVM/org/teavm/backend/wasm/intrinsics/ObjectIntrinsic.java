package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.CachedValue;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmIntUnaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;
import org.teavm.model.ValueType;

public class ObjectIntrinsic implements WasmGCInlineIntrinsic {
   private WasmGCClassInfoProvider classInfoProvider;
   private WasmFunctionTypes functionTypes;

   public ObjectIntrinsic(WasmGCClassInfoProvider classInfoProvider, WasmFunctionTypes functionTypes) {
      this.classInfoProvider = classInfoProvider;
      this.functionTypes = functionTypes;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "getObjectClassInfo":
            this.generateGetClass(invocation, context, builder);
            break;
         case "getMonitor":
            this.generateGetMonitor(invocation, context, builder);
            break;
         case "setMonitor":
            this.generateSetMonitor(invocation, context, builder);
            break;
         case "wasmGCIdentity":
            this.generateGetIdentity(invocation, context, builder);
            break;
         case "setWasmGCIdentity":
            this.generateSetIdentity(invocation, context, builder);
            break;
         case "cloneObject":
            this.generateClone(invocation, context, builder);
            break;
         default:
            throw new IllegalArgumentException();
      }
   }

   private void generateGetClass(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmGCClassInfo objectInfo = this.classInfoProvider.getClassInfo("java.lang.Object");
      WasmStructure objectStruct = objectInfo.getStructure();
      context.generate(builder, invocation.getArguments().get(0));
      builder.structGet(objectStruct, 0).structGet(objectInfo.getVirtualTableStructure(), 0);
   }

   private void generateGetMonitor(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmStructure monitorStruct = this.classInfoProvider.getClassInfo(ValueType.object("java.lang.Object$Monitor")).getStructure();
      WasmType.CompositeReference monitorType = monitorStruct.getReference();
      WasmType.CompositeReference monitorNotNullType = monitorStruct.getNonNullReference();
      WasmStructure objectStruct = this.classInfoProvider.getClassInfo(ValueType.object("java.lang.Object")).getStructure();
      WasmLocal tmpVar = context.tempVars().acquire(WasmType.ANY);
      WasmInstructionBuilder block = builder.block(monitorType);
      context.generate(block, invocation.getArguments().get(0));
      block.structGet(objectStruct, 1)
         .setLocal(tmpVar)
         .nullConst(monitorType)
         .getLocal(tmpVar)
         .test(monitorNotNullType)
         .intUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ)
         .branch(block)
         .drop()
         .getLocal(tmpVar)
         .cast(monitorNotNullType);
      context.tempVars().release(tmpVar);
   }

   private void generateSetMonitor(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmStructure objectStruct = this.classInfoProvider.getClassInfo(ValueType.object("java.lang.Object")).getStructure();
      context.generate(builder, invocation.getArguments().get(0));
      context.generate(builder, invocation.getArguments().get(1));
      builder.structSet(objectStruct, 1);
   }

   private void generateGetIdentity(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmStructure objectStruct = this.classInfoProvider.getClassInfo(ValueType.object("java.lang.Object")).getStructure();
      WasmLocal tmpVar = context.tempVars().acquire(WasmType.ANY);
      WasmInstructionBuilder block = builder.block(WasmType.INT32);
      context.generate(block, invocation.getArguments().get(0));
      block.structGet(objectStruct, 1)
         .setLocal(tmpVar)
         .i32Const(-1)
         .getLocal(tmpVar)
         .test(WasmType.SpecialReferenceKind.I31.asNonNullType())
         .negate()
         .branch(block)
         .drop()
         .getLocal(tmpVar)
         .cast(WasmType.SpecialReferenceKind.I31.asNonNullType())
         .i31Get(WasmSignedType.UNSIGNED);
      context.tempVars().release(tmpVar);
   }

   private void generateSetIdentity(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmStructure objectStruct = this.classInfoProvider.getClassInfo(ValueType.object("java.lang.Object")).getStructure();
      context.generate(builder, invocation.getArguments().get(0));
      context.generate(builder, invocation.getArguments().get(1));
      builder.i31Ref().structSet(objectStruct, 1);
   }

   private void generateClone(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      WasmGCClassInfo objectInfo = this.classInfoProvider.getClassInfo("java.lang.Object");
      WasmStructure objectStruct = objectInfo.getStructure();
      ClassInfoStruct classStruct = this.classInfoProvider.reflectionTypes().classInfo();
      context.generate(builder, invocation.getArguments().get(0));
      CachedValue cachedObj = context.valueCache().create(objectStruct.getReference(), builder);
      builder.append(cachedObj)
         .structGet(objectStruct, 0)
         .structGet(objectInfo.getVirtualTableStructure(), 0)
         .structGet(classStruct.structure(), classStruct.cloneFunctionIndex())
         .callReference(this.functionTypes.of(objectStruct.getReference(), objectStruct.getReference()));
      cachedObj.release();
   }
}
