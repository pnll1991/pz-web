package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.runtime.CResources;
import org.teavm.model.MethodReference;

public class CResourcesIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(CResources.class.getName()) && method.getName().equals("getBaseAddress");
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("resources_gen.h");
      context.writer().print("((void*) teavm_resourceData)");
   }
}
