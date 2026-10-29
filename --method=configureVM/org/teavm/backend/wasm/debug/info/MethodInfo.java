package org.teavm.backend.wasm.debug.info;

public abstract class MethodInfo {
   public abstract ClassInfo cls();

   public abstract String name();

   public String fullName() {
      return this.cls().fullName() + "." + this.name();
   }
}
