package org.teavm.common;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class CollectionUtil {
   private CollectionUtil() {
   }

   public static <T, K extends Comparable<K>> int binarySearch(List<? extends T> list, K key, Function<T, K> keyExtractor) {
      return binarySearch(list, key, keyExtractor, Comparable::compareTo);
   }

   public static <T, K> int binarySearch(List<? extends T> list, K key, Function<T, K> keyExtractor, Comparator<K> comparator) {
      int l = 0;
      int u = list.size() - 1;

      while (true) {
         int i = (l + u) / 2;
         K t = keyExtractor.apply((T)list.get(i));
         int cmp = comparator.compare(key, t);
         if (cmp == 0) {
            return i;
         }

         if (cmp > 0) {
            l = i + 1;
            if (l > u) {
               return -i - 2;
            }
         } else {
            u = i - 1;
            if (u < l) {
               return -i - 1;
            }
         }
      }
   }
}
