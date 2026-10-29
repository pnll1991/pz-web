package org.teavm.backend.javascript.codegen;

public interface LocationProvider {
   int getLine();

   int getColumn();

   int getOffset();
}
