package org.teavm.backend.javascript.rendering;

import java.io.IOException;
import java.io.Reader;
import org.teavm.rhino.javascript.CompilerEnvirons;
import org.teavm.rhino.javascript.ErrorReporter;
import org.teavm.rhino.javascript.Parser;

public class JSParser extends Parser {
   public JSParser(Object compilerEnv, Object errorReporter) {
      this((CompilerEnvirons)compilerEnv, (ErrorReporter)errorReporter);
   }

   public JSParser(CompilerEnvirons compilerEnv, ErrorReporter errorReporter) {
      super(compilerEnv, errorReporter);
   }

   public JSParser(Object compilerEnv) {
      this((CompilerEnvirons)compilerEnv);
   }

   public JSParser(CompilerEnvirons compilerEnv) {
      super(compilerEnv);
   }

   public void enterFunction() {
      this.nestingOfFunction++;
   }

   public void exitFunction() {
      this.nestingOfFunction--;
   }

   public Object parseAsObject(Reader sourceReader, String sourceURI, int lineno) throws IOException {
      return this.parse(sourceReader, sourceURI, lineno);
   }
}
