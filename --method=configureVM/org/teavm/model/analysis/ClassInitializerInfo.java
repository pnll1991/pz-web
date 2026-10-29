package org.teavm.model.analysis;

import java.util.Collections;
import java.util.List;

public interface ClassInitializerInfo {
   ClassInitializerInfo EMPTY = new ClassInitializerInfo() {
      @Override
      public boolean isDynamicInitializer(String className) {
         return true;
      }

      @Override
      public List<? extends String> getInitializationOrder() {
         return Collections.emptyList();
      }
   };

   boolean isDynamicInitializer(String var1);

   List<? extends String> getInitializationOrder();
}
