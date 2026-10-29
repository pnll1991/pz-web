package org.teavm.backend.wasm.debug.info;

public abstract class FileInfo {
   public abstract FileInfo parent();

   public abstract String name();

   public String fullName() {
      FileInfo parent = this.parent();
      return parent != null ? parent.fullName() + "/" + this.name() : this.name();
   }
}
