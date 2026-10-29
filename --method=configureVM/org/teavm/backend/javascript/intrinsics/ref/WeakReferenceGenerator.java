package org.teavm.backend.javascript.intrinsics.ref;

import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.backend.javascript.templating.JavaScriptTemplate;
import org.teavm.backend.javascript.templating.JavaScriptTemplateFactory;
import org.teavm.model.MethodReference;

public class WeakReferenceGenerator implements Generator {
   private JavaScriptTemplate template;

   public WeakReferenceGenerator(JavaScriptTemplateFactory templateFactory) {
      this.template = templateFactory.createFromResource("org/teavm/classlib/java/lang/ref/WeakReference.js");
   }

   @Override
   public void generate(GeneratorContext context, SourceWriter writer, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "<init>":
            this.template.builder("init").withContext(context).build().write(writer, 0);
            break;
         case "get":
         case "clear":
            this.template.builder(methodRef.getName()).withContext(context).build().write(writer, 0);
      }
   }
}
