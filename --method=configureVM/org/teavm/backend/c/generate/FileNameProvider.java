package org.teavm.backend.c.generate;

import org.teavm.model.ValueType;

public interface FileNameProvider {
   String fileName(String var1);

   String fileName(ValueType var1);

   String escapeName(String var1);
}
