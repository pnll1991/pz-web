package org.teavm.backend.c.generate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public class SimpleStringPool implements StringPool {
   private ObjectIntMap<String> stringIndexes = new ObjectIntHashMap();
   private final List<String> strings = new ArrayList<>();
   private final List<String> readonlyStrings = Collections.unmodifiableList(this.strings);

   @Override
   public int getStringIndex(String string) {
      int index = this.stringIndexes.getOrDefault(string, -1);
      if (index < 0) {
         index = this.strings.size();
         this.strings.add(string);
         this.stringIndexes.put(string, index);
      }

      return index;
   }

   @Override
   public List<? extends String> getStrings() {
      return this.readonlyStrings;
   }

   public void reset() {
      this.strings.clear();
      this.stringIndexes.clear();
   }
}
