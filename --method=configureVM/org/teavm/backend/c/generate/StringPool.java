package org.teavm.backend.c.generate;

import java.util.List;

public interface StringPool {
   int getStringIndex(String var1);

   List<? extends String> getStrings();
}
