package org.teavm.backend.wasm.intrinsics;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmNullCondition;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class WeakReferenceIntrinsic implements WasmGCBodyIntrinsic {
   private WasmGCCodeGenContext context;
   private WasmFunction createWeakReferenceFunction;

   public WeakReferenceIntrinsic(WasmGCCodeGenContext context) {
      this.context = context;
   }

   @Override
   public void apply(MethodReference method, WasmFunction function) {
      switch (method.getName()) {
         case "<init>":
            this.generateConstructor(function);
            break;
         case "get":
            this.generateDeref(function);
            break;
         case "clear":
            this.generateClear(function);
      }
   }

   private void generateConstructor(WasmFunction function) {
      WasmStructure weakRefStruct = this.context.classInfoProvider().getClassInfo(WeakReference.class.getName()).getStructure();
      WasmLocal thisLocal = new WasmLocal(weakRefStruct.getReference(), "this");
      WasmLocal valueLocal = new WasmLocal(this.context.typeMapper().mapType(ValueType.parse(Object.class)), "value");
      WasmLocal queueLocal = new WasmLocal(this.context.typeMapper().mapType(ValueType.parse(ReferenceQueue.class)), "queue");
      function.add(thisLocal);
      function.add(valueLocal);
      function.add(queueLocal);
      WasmFunction weakRefConstructor = this.getCreateWeakReferenceFunction();
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(thisLocal);
      body.getLocal(valueLocal);
      body.getLocal(thisLocal);
      body.getLocal(queueLocal);
      body.call(weakRefConstructor);
      body.structSet(weakRefStruct, 2);
   }

   private void generateDeref(WasmFunction function) {
      WasmStructure weakRefStruct = this.context.classInfoProvider().getClassInfo(WeakReference.class.getName()).getStructure();
      WasmType.CompositeReference objectType = this.context.classInfoProvider().getClassInfo("java.lang.Object").getType();
      WasmType.CompositeReference thisType = weakRefStruct.getReference();
      WasmLocal thisLocal = new WasmLocal(thisType, "this");
      function.add(thisLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      WasmInstructionBuilder block = body.block(WasmType.EXTERN);
      block.getLocal(thisLocal);
      block.structGet(weakRefStruct, 2).nullBranch(WasmNullCondition.NOT_NULL, block).nullConst(objectType).return_();
      body.call(this.createDerefFunction());
   }

   private void generateClear(WasmFunction function) {
      WasmStructure weakRefStruct = this.context.classInfoProvider().getClassInfo(WeakReference.class.getName()).getStructure();
      WasmType.CompositeReference thisType = weakRefStruct.getReference();
      WasmLocal thisLocal = new WasmLocal(thisType, "this");
      function.add(thisLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(thisLocal);
      body.nullConst(WasmType.EXTERN).structSet(weakRefStruct, 2);
   }

   private WasmFunction getCreateWeakReferenceFunction() {
      if (this.createWeakReferenceFunction == null) {
         WasmFunction function = new WasmFunction(
            this.context
               .functionTypes()
               .of(
                  WasmType.EXTERN,
                  this.context.typeMapper().mapType(ValueType.parse(Object.class)),
                  this.context.typeMapper().mapType(ValueType.parse(WeakReference.class)),
                  this.context.typeMapper().mapType(ValueType.parse(ReferenceQueue.class))
               )
         );
         function.setName(this.context.names().topLevel("teavm@createWeakReference"));
         function.setImportName("createWeakRef");
         function.setImportModule("teavm");
         this.context.module().functions.add(function);
         this.createWeakReferenceFunction = function;
      }

      return this.createWeakReferenceFunction;
   }

   private WasmFunction createDerefFunction() {
      WasmFunction function = new WasmFunction(
         this.context.functionTypes().of(this.context.typeMapper().mapType(ValueType.parse(Object.class)), WasmType.EXTERN)
      );
      function.setName(this.context.names().topLevel("teavm@deref"));
      function.setImportName("deref");
      function.setImportModule("teavm");
      this.context.module().functions.add(function);
      return function;
   }
}
