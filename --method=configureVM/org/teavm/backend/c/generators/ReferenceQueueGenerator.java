package org.teavm.backend.c.generators;

import java.lang.ref.ReferenceQueue;
import org.teavm.model.MethodReference;

public class ReferenceQueueGenerator implements Generator {
   private static final MethodReference OBJECT_INIT = new MethodReference(Object.class, "<init>", void.class);

   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(ReferenceQueue.class.getName());
   }

   @Override
   public void generate(GeneratorContext context, MethodReference method) {
      switch (method.getName()) {
         case "<init>":
            context.importMethod(OBJECT_INIT, false);
            context.writer().print(context.names().forMethod(OBJECT_INIT)).print("(").print(context.parameterName(0)).println(");");
            break;
         case "poll":
            context.writer().print("return teavm_reference_poll(");
            context.writer().print("(TeaVM_ReferenceQueue*) ").print(context.parameterName(0));
            context.writer().println(");");
      }
   }
}
