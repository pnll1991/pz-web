package org.teavm.cache;

import java.util.function.Supplier;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.model.MethodReference;

public class EmptyMethodNodeCache implements MethodNodeCache {
   public static final EmptyMethodNodeCache INSTANCE = new EmptyMethodNodeCache();

   private EmptyMethodNodeCache() {
   }

   @Override
   public AstCacheEntry get(MethodReference methodReference, CacheStatus cacheStatus) {
      return null;
   }

   @Override
   public void store(MethodReference methodReference, AstCacheEntry node, Supplier<String[]> dependencies) {
   }

   @Override
   public AsyncMethodNode getAsync(MethodReference methodReference, CacheStatus cacheStatus) {
      return null;
   }

   @Override
   public void storeAsync(MethodReference methodReference, AsyncMethodNode node, Supplier<String[]> dependencies) {
   }
}
