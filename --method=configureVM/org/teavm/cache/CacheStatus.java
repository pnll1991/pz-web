package org.teavm.cache;

import org.teavm.model.MethodReference;

public interface CacheStatus {
   boolean isStaleClass(String var1);

   boolean isStaleMethod(MethodReference var1);
}
