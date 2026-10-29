package org.teavm.model;

import java.util.function.Supplier;
import org.teavm.cache.CacheStatus;

public interface ProgramCache {
   Program get(MethodReference var1, CacheStatus var2);

   void store(MethodReference var1, Program var2, Supplier<String[]> var3);
}
