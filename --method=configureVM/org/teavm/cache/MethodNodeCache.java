package org.teavm.cache;

import java.util.function.Supplier;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.model.MethodReference;

public interface MethodNodeCache {
   AstCacheEntry get(MethodReference var1, CacheStatus var2);

   void store(MethodReference var1, AstCacheEntry var2, Supplier<String[]> var3);

   AsyncMethodNode getAsync(MethodReference var1, CacheStatus var2);

   void storeAsync(MethodReference var1, AsyncMethodNode var2, Supplier<String[]> var3);
}
