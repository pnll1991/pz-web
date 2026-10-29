package org.teavm.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;

public class CompositeClassHolderSource implements ClassHolderSource {
   private List<ClassHolderSource> innerSources;
   private Map<String, ClassHolder> cache = new HashMap<>();

   public CompositeClassHolderSource(List<ClassHolderSource> innerSources) {
      this.innerSources = new ArrayList<>(innerSources);
   }

   @Override
   public ClassHolder get(String name) {
      return this.cache.computeIfAbsent(name, n -> {
         for (ClassHolderSource innerSource : this.innerSources) {
            ClassHolder cls = innerSource.get(n);
            if (cls != null) {
               return cls;
            }
         }

         return null;
      });
   }
}
