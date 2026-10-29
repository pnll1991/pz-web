package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;
import org.teavm.runtime.GC;

public class GCIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals(GC.class.getName())) {
         return false;
      }

      switch (method.getName()) {
         case "gcStorageAddress":
         case "gcStorageSize":
         case "heapAddress":
         case "regionsAddress":
         case "regionMaxCount":
         case "availableBytes":
         case "regionSize":
         case "minAvailableBytes":
         case "maxAvailableBytes":
         case "resizeHeap":
         case "cardTable":
         case "writeBarrier":
         case "canShrinkHeap":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "resizeHeap":
            context.writer().print("teavm_gc_resizeHeap(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")");
            break;
         case "writeBarrier":
            context.writer().print("teavm_gc_writeBarrier(");
            context.emit(invocation.getArguments().get(0));
            context.writer().print(")");
            break;
         case "canShrinkHeap":
            context.writer().print("1");
            break;
         default:
            context.includes().includePath("heaptrace.h");
            context.writer().print("teavm_gc_").print(invocation.getMethod().getName());
      }
   }
}
