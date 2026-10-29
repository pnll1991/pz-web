package org.teavm.cache;

import org.teavm.model.MethodReference;

public interface IncrementalDependencyRegistration {
   void setNoCache(String var1);

   void setNoCache(MethodReference var1);

   void addDependencies(String var1, String... var2);

   void addDependencies(MethodReference var1, String... var2);
}
