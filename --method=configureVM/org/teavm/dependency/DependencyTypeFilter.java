package org.teavm.dependency;

import java.util.BitSet;

public interface DependencyTypeFilter {
   boolean match(DependencyType var1);

   default int[] tryExtract(BitSet types) {
      return null;
   }
}
