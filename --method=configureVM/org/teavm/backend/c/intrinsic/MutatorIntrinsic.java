package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;
import org.teavm.runtime.Mutator;

public class MutatorIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals(Mutator.class.getName())) {
         return false;
      }

      switch (method.getName()) {
         case "getStaticGCRoots":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "getStaticGCRoots":
            context.writer().print("teavm_gc_staticRoots");
      }
   }
}
