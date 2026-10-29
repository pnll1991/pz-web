package org.teavm.backend.javascript.intrinsics.ref;

import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.backend.javascript.templating.JavaScriptTemplate;
import org.teavm.backend.javascript.templating.JavaScriptTemplateFactory;
import org.teavm.model.MethodReference;

public class ReferenceQueueGenerator implements Generator {
   private JavaScriptTemplate template;

   @Override
   public void generate(GeneratorContext context, SourceWriter writer, MethodReference methodRef) {
      this.ensureTemplate(context);
      switch (methodRef.getName()) {
         case "<init>":
            this.template.builder("init").withContext(context).build().write(writer, 0);
            break;
         case "poll":
            this.template.builder("poll").withContext(context).build().write(writer, 0);
      }
   }

   private void ensureTemplate(GeneratorContext context) {
      if (this.template == null) {
         this.template = new JavaScriptTemplateFactory(context.getClassLoader(), context.getClassSource())
            .createFromResource("org/teavm/classlib/java/lang/ref/ReferenceQueue.js");
      }
   }
}
