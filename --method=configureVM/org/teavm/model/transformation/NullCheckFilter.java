package org.teavm.model.transformation;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;

public interface NullCheckFilter {
   NullCheckFilter EMPTY = new NullCheckFilter() {
      @Override
      public boolean apply(FieldReference field) {
         return true;
      }

      @Override
      public boolean apply(MethodReference method) {
         return true;
      }
   };

   boolean apply(FieldReference var1);

   boolean apply(MethodReference var1);
}
