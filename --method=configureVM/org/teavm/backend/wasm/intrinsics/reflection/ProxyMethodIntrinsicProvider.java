package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.model.MethodReference;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.vm.intrinsic.IntrinsicProvider;

public class ProxyMethodIntrinsicProvider implements IntrinsicProvider<WasmGCInlineIntrinsic> {
   private ReflectionDependencyListener reflection;
   private ProxyIntrinsicContext intrinsicContext;

   public ProxyMethodIntrinsicProvider(ReflectionDependencyListener reflection, ProxyIntrinsicContext intrinsicContext) {
      this.reflection = reflection;
      this.intrinsicContext = intrinsicContext;
   }

   public WasmGCInlineIntrinsic getIntrinsic(MethodReference method) {
      if (this.reflection.isGeneratedProxyClass(method.getClassName())) {
         MethodReference methodToAcquire = this.reflection.getProxyWorkerAcquireMethod(method);
         if (methodToAcquire != null) {
            return (invocation, context, builder) -> builder.getGlobal(this.intrinsicContext.getMethodGlobal(methodToAcquire));
         }
      }

      return null;
   }
}
