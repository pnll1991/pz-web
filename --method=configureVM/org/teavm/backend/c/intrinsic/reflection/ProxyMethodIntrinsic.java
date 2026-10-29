package org.teavm.backend.c.intrinsic.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;
import org.teavm.reflection.ReflectionDependencyListener;

public class ProxyMethodIntrinsic implements Intrinsic {
   private ReflectionDependencyListener reflection;
   private ProxyIntrinsicContext intrinsicContext;

   public ProxyMethodIntrinsic(ReflectionDependencyListener reflection, ProxyIntrinsicContext intrinsicContext) {
      this.reflection = reflection;
      this.intrinsicContext = intrinsicContext;
   }

   @Override
   public boolean canHandle(MethodReference method) {
      return !this.reflection.isGeneratedProxyClass(method.getClassName()) ? false : this.reflection.getProxyWorkerAcquireMethod(method) != null;
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      MethodReference methodToAcquire = this.reflection.getProxyWorkerAcquireMethod(invocation.getMethod());
      int index = this.intrinsicContext.getMethodIndex(methodToAcquire);
      context.includes().includePath("proxy.h");
      context.writer().print("teavm_getProxyMethod(" + index + ")");
   }
}
