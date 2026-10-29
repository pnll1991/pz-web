package org.teavm.backend.javascript.spi;

import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.model.MethodReference;

public interface Generator {
   void generate(GeneratorContext var1, SourceWriter var2, MethodReference var3);
}
