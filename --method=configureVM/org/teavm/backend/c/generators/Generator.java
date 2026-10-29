package org.teavm.backend.c.generators;

import org.teavm.model.MethodReference;

public interface Generator {
   boolean canHandle(MethodReference var1);

   void generate(GeneratorContext var1, MethodReference var2);
}
