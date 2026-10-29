package org.teavm.backend.c.intrinsic.reflection;

import java.lang.reflect.Proxy;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.model.MethodReference;

public class ProxyIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals(Proxy.class.getName())) {
         return false;
      }

      switch (method.getName()) {
         case "registerProxyClasses":
         case "wrapDependency":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "registerProxyClasses":
            context.includes().includePath("proxy.h");
            context.writer().print("teavm_proxy_registerClasses()");
            break;
         case "wrapDependency":
            context.emit(invocation.getArguments().get(0));
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }
}
