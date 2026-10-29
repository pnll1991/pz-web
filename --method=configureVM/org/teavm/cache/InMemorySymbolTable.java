package org.teavm.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemorySymbolTable implements SymbolTable {
   private List<String> symbols = new ArrayList<>();
   private Map<String, Integer> indexes = new HashMap<>();

   @Override
   public String at(int index) {
      return this.symbols.get(index);
   }

   @Override
   public int lookup(String symbol) {
      Integer index = this.indexes.get(symbol);
      if (index == null) {
         index = this.symbols.size();
         this.symbols.add(symbol);
         this.indexes.put(symbol, index);
      }

      return index;
   }

   public void invalidate() {
      this.symbols.clear();
      this.indexes.clear();
   }
}
