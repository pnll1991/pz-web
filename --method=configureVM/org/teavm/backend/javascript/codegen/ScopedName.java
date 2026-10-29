package org.teavm.backend.javascript.codegen;

public class ScopedName {
   public final String name;
   public final boolean scoped;

   public ScopedName(String name, boolean scoped) {
      this.name = name;
      this.scoped = scoped;
   }
}
