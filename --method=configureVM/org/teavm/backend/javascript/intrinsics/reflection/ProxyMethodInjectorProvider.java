package org.teavm.backend.javascript.intrinsics.reflection;

import java.util.function.Function;
import org.teavm.backend.javascript.ProviderContext;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.model.MethodReference;
import org.teavm.reflection.ReflectionDependencyListener;

public class ProxyMethodInjectorProvider implements Function<ProviderContext, Injector> {
   private ReflectionDependencyListener reflection;
   private ProxyGeneratorContext proxyContext;

   public ProxyMethodInjectorProvider(ReflectionDependencyListener reflection, ProxyGeneratorContext proxyContext) {
      this.reflection = reflection;
      this.proxyContext = proxyContext;
   }

   public Injector apply(ProviderContext context) {
      if (this.reflection.isGeneratedProxyClass(context.getMethod().getClassName())) {
         MethodReference methodToAcquire = this.reflection.getProxyWorkerAcquireMethod(context.getMethod());
         if (methodToAcquire != null) {
            return (ctx, method) -> ctx.getWriter().appendFunction("$rt_proxyMethods").append(".").append(this.proxyContext.getPropertyName(methodToAcquire));
         }
      }

      return null;
   }
}
