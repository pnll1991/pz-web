package org.teavm.backend.javascript;

import java.util.function.Consumer;
import org.teavm.backend.javascript.codegen.NamingStrategy;
import org.teavm.backend.javascript.codegen.SourceWriter;

public class ExportedDeclaration {
   final Consumer<SourceWriter> name;
   final Consumer<NamingStrategy> nameFreq;
   final String alias;

   public ExportedDeclaration(Consumer<SourceWriter> name, Consumer<NamingStrategy> nameFreq, String alias) {
      this.name = name;
      this.nameFreq = nameFreq;
      this.alias = alias;
   }
}
