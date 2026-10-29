package org.teavm.backend.c.generate;

public interface FileGenerator {
   CodeWriter writer();

   IncludeManager includes();
}
