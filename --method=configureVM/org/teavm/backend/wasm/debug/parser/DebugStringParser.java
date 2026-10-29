package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;

public class DebugStringParser extends DebugSectionParser {
   private String[] strings;

   public DebugStringParser() {
      super("teavm_str");
   }

   @Override
   protected void doParse() {
      ArrayList<String> strings = new ArrayList<>();

      while (this.ptr < this.data.length) {
         strings.add(this.readString());
      }

      this.strings = strings.toArray(new String[0]);
   }

   public String getString(int ref) {
      return this.strings[ref];
   }
}
