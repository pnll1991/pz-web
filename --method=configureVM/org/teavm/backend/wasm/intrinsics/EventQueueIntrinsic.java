package org.teavm.backend.wasm.intrinsics;

import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.model.MethodReference;
import org.teavm.runtime.EventQueue;

public class EventQueueIntrinsic implements WasmGCBodyIntrinsic {
   private WasmGCCodeGenContext context;

   public EventQueueIntrinsic(WasmGCCodeGenContext context) {
      this.context = context;
   }

   @Override
   public void apply(MethodReference method, WasmFunction function) {
      WasmType.CompositeReference eventType = this.context.classInfoProvider().getClassInfo(EventQueue.Event.class.getName()).getType();
      WasmFunctionType eventCallbackType = this.context.functionTypes().of(null, eventType);
      WasmFunction runtimeFn = new WasmFunction(this.context.functionTypes().of(WasmType.INT32, eventType, eventCallbackType.getReference(), WasmType.FLOAT64));
      runtimeFn.setName(this.context.names().topLevel("teavmAsync.offer"));
      runtimeFn.setImportModule("teavmAsync");
      runtimeFn.setImportName("offer");
      this.context.module().functions.add(runtimeFn);
      WasmFunction callerFn = this.context.functions().forStaticMethod(new MethodReference(EventQueue.class, "run", EventQueue.Event.class, void.class));
      callerFn.setReferenced(true);
      WasmLocal eventVar = new WasmLocal(eventType, "event");
      WasmLocal timeVar = new WasmLocal(WasmType.FLOAT64, "time");
      function.add(eventVar);
      function.add(timeVar);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(eventVar).funcRef(callerFn).getLocal(timeVar).call(runtimeFn);
   }
}
