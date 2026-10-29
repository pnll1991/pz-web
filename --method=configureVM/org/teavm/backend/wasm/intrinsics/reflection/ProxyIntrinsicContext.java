package org.teavm.backend.wasm.intrinsics.reflection;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenContext;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.instruction.WasmNullConstant;
import org.teavm.model.MethodReference;

public class ProxyIntrinsicContext {
   private WasmGCCodeGenContext genContext;
   private Map<MethodReference, WasmGlobal> globals = new HashMap<>();

   public ProxyIntrinsicContext(WasmGCCodeGenContext genContext) {
      this.genContext = genContext;
   }

   public WasmGlobal getMethodGlobal(MethodReference methodRef) {
      return this.globals.computeIfAbsent(methodRef, ref -> {
         String name = this.genContext.names().topLevel("teavm.proxy:" + this.genContext.names().suggestForMethod(ref));
         WasmGCClassInfo methodInfo = this.genContext.classInfoProvider().getClassInfo(Method.class.getName());
         WasmGlobal global = new WasmGlobal(name, methodInfo.getType());
         global.getInitialValue().add(new WasmNullConstant(methodInfo.getStructure().getReference()));
         this.genContext.module().globals.add(global);
         return global;
      });
   }
}
