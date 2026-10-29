package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;
import org.teavm.runtime.MemoryTrace;

public class MemoryTraceIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(MemoryTrace.class.getName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      context.includes().includePath("heaptrace.h");
      context.writer().print("teavm_gc_").print(invocation.getMethod().getName()).print("(");
      if (!invocation.getArguments().isEmpty()) {
         context.emit(invocation.getArguments().get(0));

         for (int i = 1; i < invocation.getArguments().size(); i++) {
            context.writer().print(", ");
            context.emit(invocation.getArguments().get(i));
         }
      }

      context.writer().print(")");
   }
}
