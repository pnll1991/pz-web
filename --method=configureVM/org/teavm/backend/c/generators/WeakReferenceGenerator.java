package org.teavm.backend.c.generators;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import org.teavm.model.MethodReference;

public class WeakReferenceGenerator implements Generator {
   private static final MethodReference REFERENCE_INIT = new MethodReference(Reference.class, "<init>", void.class);

   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(WeakReference.class.getName());
   }

   @Override
   public void generate(GeneratorContext context, MethodReference method) {
      switch (method.getName()) {
         case "<init>":
            context.importMethod(REFERENCE_INIT, false);
            context.writer().print(context.names().forMethod(REFERENCE_INIT)).print("(").print(context.parameterName(0)).println(");");
            context.writer().print("teavm_reference_init(");
            context.writer().print("(TeaVM_Reference*) ").print(context.parameterName(0)).print(", ");
            context.writer().print(context.parameterName(1)).print(", ");
            if (method.parameterCount() == 2) {
               context.writer().print(context.parameterName(2));
            } else {
               context.writer().print("NULL");
            }

            context.writer().println(");");
            break;
         case "get":
            context.writer().print("return teavm_reference_get(");
            context.writer().print("(TeaVM_Reference*) ").print(context.parameterName(0));
            context.writer().println(");");
            break;
         case "clear":
            context.writer().print("teavm_reference_clear(");
            context.writer().print("(TeaVM_Reference*) ").print(context.parameterName(0));
            context.writer().println(");");
            break;
         case "isEnqueued":
            context.writer().print("return teavm_reference_isEnqueued(");
            context.writer().print("(TeaVM_Reference*) ").print(context.parameterName(0));
            context.writer().println(");");
            break;
         case "enqueue":
            context.writer().print("return teavm_reference_enqueue(");
            context.writer().print("(TeaVM_Reference*) ").print(context.parameterName(0));
            context.writer().println(");");
      }
   }
}
