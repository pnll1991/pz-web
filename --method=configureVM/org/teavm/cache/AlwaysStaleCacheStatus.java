package org.teavm.cache;

import org.teavm.model.MethodReference;

public class AlwaysStaleCacheStatus implements CacheStatus {
   public static final AlwaysStaleCacheStatus INSTANCE = new AlwaysStaleCacheStatus();

   private AlwaysStaleCacheStatus() {
   }

   @Override
   public boolean isStaleClass(String className) {
      return true;
   }

   @Override
   public boolean isStaleMethod(MethodReference method) {
      return true;
   }
}
