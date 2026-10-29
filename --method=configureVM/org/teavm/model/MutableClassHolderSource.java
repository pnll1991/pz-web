package org.teavm.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MutableClassHolderSource implements ListableClassHolderSource {
   private Map<String, ClassHolder> classes = new LinkedHashMap<>();

   @Override
   public Set<String> getClassNames() {
      return this.classes.keySet();
   }

   @Override
   public ClassHolder get(String name) {
      return this.classes.get(name);
   }

   public void putClassHolder(ClassHolder classHolder) {
      this.classes.put(classHolder.getName(), classHolder);
   }
}
