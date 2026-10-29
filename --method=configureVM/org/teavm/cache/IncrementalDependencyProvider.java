package org.teavm.cache;

import org.teavm.model.MethodReference;

public interface IncrementalDependencyProvider {
   boolean isNoCache(String var1);

   boolean isNoCache(MethodReference var1);

   String[] getDependencies(String var1);

   String[] getDependencies(MethodReference var1);
}
