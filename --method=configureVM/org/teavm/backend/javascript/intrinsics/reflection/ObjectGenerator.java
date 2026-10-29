package org.teavm.backend.javascript.intrinsics.reflection;

import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.model.MethodReference;

public class ObjectGenerator implements Generator {
   @Override
   public void generate(GeneratorContext context, SourceWriter writer, MethodReference methodRef) {
      writer.append("return ").append(context.getParameterName(1)).append(".constructor;").softNewLine();
   }
}
