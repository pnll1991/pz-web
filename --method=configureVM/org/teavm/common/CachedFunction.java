package org.teavm.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CachedFunction<T, R> implements Function<T, R> {
   private Function<T, R> innerFunction;
   private Map<T, CachedFunction.Wrapper<R>> cache = new LinkedHashMap<>();
   private List<CachedFunction.KeyListener<T>> keyListeners = new ArrayList<>();

   public CachedFunction(Function<T, R> innerFunction) {
      this.innerFunction = innerFunction;
   }

   public R getKnown(T preimage) {
      CachedFunction.Wrapper<R> wrapper = this.cache.get(preimage);
      return wrapper != null ? wrapper.value : null;
   }

   @Override
   public R apply(T t) {
      CachedFunction.Wrapper<R> wrapper = this.cache.get(t);
      if (wrapper == null) {
         wrapper = new CachedFunction.Wrapper<>();
         this.cache.put(t, wrapper);
         wrapper.value = this.innerFunction.apply(t);
         wrapper.computed = true;

         for (CachedFunction.KeyListener<T> listener : this.keyListeners) {
            listener.keyAdded(t);
         }
      }

      if (!wrapper.computed) {
         throw new IllegalStateException("Recursive calls are not allowed");
      } else {
         return wrapper.value;
      }
   }

   public void replace(T preimage, R value) {
      this.cache.get(preimage).value = (S)value;
   }

   public void invalidate(T preimage) {
      this.cache.remove(preimage);
   }

   public boolean caches(T preimage) {
      return this.cache.get(preimage) != null;
   }

   public Collection<T> getCachedPreimages() {
      return new LinkedHashSet<>(this.cache.keySet());
   }

   public void addKeyListener(CachedFunction.KeyListener<T> listener) {
      this.keyListeners.add(listener);
   }

   public interface KeyListener<S> {
      void keyAdded(S var1);
   }

   private static class Wrapper<S> {
      S value;
      boolean computed;
   }
}
