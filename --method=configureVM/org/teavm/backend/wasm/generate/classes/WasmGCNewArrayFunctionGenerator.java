package org.teavm.backend.wasm.generate.classes;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmIntUnaryOperation;
import org.teavm.model.ValueType;

class WasmGCNewArrayFunctionGenerator {
   private WasmModule module;
   private WasmFunctionTypes functionTypes;
   private WasmGCClassInfoProvider classInfoProvider;
   private WasmGCNameProvider names;
   private Queue<Runnable> queue;
   private WasmFunction newObjectArrayFunction;

   WasmGCNewArrayFunctionGenerator(
      WasmModule module, WasmFunctionTypes functionTypes, WasmGCClassInfoProvider classInfoProvider, WasmGCNameProvider names, Queue<Runnable> queue
   ) {
      this.module = module;
      this.functionTypes = functionTypes;
      this.classInfoProvider = classInfoProvider;
      this.names = names;
      this.queue = queue;
   }

   WasmFunction generateNewArrayFunction(ValueType itemType) {
      if (itemType instanceof ValueType.Primitive) {
         ClassInfoStruct classInfoType = this.classInfoProvider.reflectionTypes().classInfo();
         WasmGCClassInfo classInfo = this.classInfoProvider.getClassInfo(ValueType.arrayOf(itemType));
         WasmFunctionType functionType = new WasmFunctionType(null, classInfo.getType(), List.of(classInfoType.structure().getReference(), WasmType.INT32));
         this.module.types.add(functionType);
         functionType.setFinal(true);
         functionType.getSupertypes().add(classInfoType.newArrayFunctionType());
         WasmFunction function = new WasmFunction(functionType);
         function.setName(this.names.topLevel("Array<" + this.names.suggestForType(itemType) + ">@new"));
         this.module.functions.add(function);
         this.queue
            .add(
               () -> {
                  WasmLocal clsLocal = new WasmLocal(classInfoType.structure().getReference(), "this");
                  WasmLocal sizeLocal = new WasmLocal(WasmType.INT32, "length");
                  function.add(clsLocal);
                  function.add(sizeLocal);
                  WasmLocal targetVar = new WasmLocal(classInfo.getType(), "result");
                  function.add(targetVar);
                  WasmType.CompositeReference wasmArrayType = (WasmType.CompositeReference)classInfo.getStructure().getFields().get(2).getUnpackedType();
                  WasmArray wasmArray = (WasmArray)wasmArrayType.composite;
                  function.getBody()
                     .builder()
                     .getGlobal(classInfo.getVirtualTablePointer())
                     .nullConst(WasmType.EQ)
                     .getLocal(sizeLocal)
                     .arrayNewDefault(wasmArray)
                     .structNew(classInfo.getStructure());
               }
            );
         return function;
      } else {
         return this.getNewObjectArrayFunction();
      }
   }

   WasmFunction getNewObjectArrayFunction() {
      if (this.newObjectArrayFunction == null) {
         this.newObjectArrayFunction = this.generateNewObjectArrayFunction();
      }

      return this.newObjectArrayFunction;
   }

   private WasmFunction generateNewObjectArrayFunction() {
      WasmGCClassInfo classInfo = this.classInfoProvider.getClassInfo(ValueType.arrayOf(ValueType.object("java.lang.Object")));
      ClassInfoStruct classInfoType = this.classInfoProvider.reflectionTypes().classInfo();
      WasmFunctionType functionType = new WasmFunctionType(null, classInfo.getType(), List.of(classInfoType.structure().getReference(), WasmType.INT32));
      this.module.types.add(functionType);
      functionType.setFinal(true);
      functionType.getSupertypes().add(classInfoType.newArrayFunctionType());
      WasmFunction function = new WasmFunction(functionType);
      function.setName(this.names.topLevel("Array<" + this.names.suggestForClass("java.lang.Object") + ">@new"));
      this.module.functions.add(function);
      this.queue
         .add(
            () -> {
               WasmLocal clsLocal = new WasmLocal(classInfoType.structure().getReference(), "this");
               WasmLocal sizeLocal = new WasmLocal(WasmType.INT32, "length");
               function.add(clsLocal);
               function.add(sizeLocal);
               WasmLocal targetVar = new WasmLocal(classInfo.getType(), "result");
               function.add(targetVar);
               WasmType.CompositeReference wasmArrayType = (WasmType.CompositeReference)classInfo.getStructure().getFields().get(2).getUnpackedType();
               WasmArray wasmArray = (WasmArray)wasmArrayType.composite;
               function.getBody()
                  .builder()
                  .getLocal(clsLocal)
                  .call(this.classInfoProvider.getGetArrayClassFunction())
                  .structGet(classInfoType.structure(), classInfoType.vtableIndex())
                  .nullConst(WasmType.EQ)
                  .getLocal(sizeLocal)
                  .arrayNewDefault(wasmArray)
                  .structNew(classInfo.getStructure());
            }
         );
      return function;
   }

   WasmFunction generateNewMultiArrayFunction(int depth) {
      ValueType.Array arrayType = ValueType.arrayOf(ValueType.object("java.lang.Object"));
      WasmGCClassInfo arrayClass = this.classInfoProvider.getClassInfo(arrayType);
      WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
      ClassInfoStruct classInfoType = this.classInfoProvider.reflectionTypes().classInfo();
      WasmType[] parameterTypes = new WasmType[depth + 1];
      Arrays.fill(parameterTypes, WasmType.INT32);
      parameterTypes[0] = classInfoType.structure().getReference();
      WasmFunctionType functionType = this.functionTypes.of(arrayClass.getType(), parameterTypes);
      WasmFunction function = new WasmFunction(functionType);
      function.setName(this.names.topLevel(this.names.suggestForType(arrayType) + "@new:" + depth));
      this.module.functions.add(function);
      this.queue.add(() -> {
         WasmLocal itemTypeLocal = new WasmLocal(classInfoType.structure().getReference(), "itemType");
         function.add(itemTypeLocal);
         WasmLocal[] dimensionLocals = new WasmLocal[depth];

         for (int i = 0; i < depth; i++) {
            WasmLocal dimensionLocal = new WasmLocal(WasmType.INT32, "dim" + i);
            dimensionLocals[i] = dimensionLocal;
            function.add(dimensionLocal);
         }

         WasmLocal indexLocal = new WasmLocal(WasmType.INT32, "index");
         WasmLocal nextItemTypeLocal = new WasmLocal(classInfoType.structure().getReference(), "nextItemType");
         function.add(indexLocal);
         function.add(nextItemTypeLocal);
         WasmField dataField = arrayClass.getStructure().getFields().get(2);
         WasmType.CompositeReference dataFieldTypeRef = (WasmType.CompositeReference)dataField.getUnpackedType();
         WasmArray dataArray = (WasmArray)dataFieldTypeRef.composite;
         WasmLocal dataLocal = new WasmLocal(dataArray.getReference(), "data");
         function.add(dataLocal);
         WasmLocal resultVar = new WasmLocal(arrayClass.getType(), "result");
         function.add(resultVar);
         WasmFunction allocFunction = this.getNewObjectArrayFunction();
         WasmInstructionBuilder body = function.getBody().builder();
         body.getLocal(itemTypeLocal).getLocal(dimensionLocals[0]).call(allocFunction).teeLocal(resultVar);
         body.structGet(arrayClass.getStructure(), 2).setLocal(dataLocal);
         body.getLocal(itemTypeLocal).structGet(classInfoType.structure(), classInfoType.itemTypeIndex()).setLocal(nextItemTypeLocal);
         WasmInstructionBuilder guardBody = body.block();
         guardBody.getLocal(dimensionLocals[0]).intUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ).branch(guardBody);
         WasmLocal createNextArrayLocal = null;
         WasmFunctionType nextFunctionType = null;
         WasmFunction nextFunction = null;
         if (depth == 2) {
            nextFunctionType = this.functionTypes.of(objectClass.getType(), classInfoType.structure().getReference(), WasmType.INT32);
            createNextArrayLocal = new WasmLocal(nextFunctionType.getReference(), "createNextArray");
            function.add(createNextArrayLocal);
            guardBody.getLocal(nextItemTypeLocal).structGet(classInfoType.structure(), classInfoType.newArrayFunctionIndex()).setLocal(createNextArrayLocal);
         } else {
            nextFunction = this.classInfoProvider.getMultiArrayConstructor(depth - 1);
         }

         WasmInstructionBuilder loopBody = guardBody.loop();
         loopBody.getLocal(dataLocal).getLocal(indexLocal);
         loopBody.getLocal(nextItemTypeLocal);

         for (int i = 1; i < depth; i++) {
            loopBody.getLocal(dimensionLocals[i]);
         }

         if (depth == 2) {
            loopBody.getLocal(createNextArrayLocal).callReference(nextFunctionType);
         } else {
            loopBody.call(nextFunction);
         }

         loopBody.arraySet(dataArray);
         loopBody.getLocal(indexLocal).i32Const(1).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD).teeLocal(indexLocal);
         loopBody.getLocal(dimensionLocals[0]).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_UNSIGNED).branch(loopBody);
         body.getLocal(resultVar);
      });
      return function;
   }
}
