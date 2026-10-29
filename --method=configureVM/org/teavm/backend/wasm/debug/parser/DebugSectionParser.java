package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import java.util.List;
import org.teavm.common.binary.BinaryParser;

public abstract class DebugSectionParser extends BinaryParser {
   private String name;
   private List<DebugSectionParser> dependantSections = new ArrayList<>();
   private boolean ready;
   private int unsatisfiedDependencies;

   protected DebugSectionParser(String name, DebugSectionParser... dependencies) {
      this.name = name;

      for (DebugSectionParser dependency : dependencies) {
         dependency.dependantSections.add(this);
      }

      this.unsatisfiedDependencies = dependencies.length;
   }

   public String name() {
      return this.name;
   }

   public boolean ready() {
      return this.ready;
   }

   public void parse(byte[] data) {
      this.data = data;
      if (this.unsatisfiedDependencies == 0) {
         this.parse();
      }
   }

   private void parse() {
      this.doParse();
      this.ready = true;
      this.data = null;

      for (DebugSectionParser dependant : this.dependantSections) {
         if (--dependant.unsatisfiedDependencies == 0 && dependant.data != null) {
            dependant.parse();
         }
      }
   }

   protected abstract void doParse();
}
