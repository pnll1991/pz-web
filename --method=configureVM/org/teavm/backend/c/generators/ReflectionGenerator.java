package org.teavm.backend.c.generators;

import org.teavm.backend.c.generate.GenerationContext;

public interface ReflectionGenerator {
   boolean isApplicable(GenerationContext var1, String var2);

   void generate(ReflectionGeneratorContext var1, String var2);
}
