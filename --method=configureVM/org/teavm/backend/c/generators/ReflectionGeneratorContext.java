package org.teavm.backend.c.generators;

import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.backend.c.generate.GenerationContext;
import org.teavm.backend.c.generate.IncludeManager;

public interface ReflectionGeneratorContext {
   GenerationContext globalContext();

   CodeWriter writer();

   IncludeManager includes();

   void addToInitializer(ReflectionPartGenerator var1);

   void addTopLevel(ReflectionPartGenerator var1);
}
