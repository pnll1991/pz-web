package org.teavm.backend.wasm.debug.info;

public abstract class PackageInfo {
   public abstract PackageInfo parent();

   public abstract String name();

   public final String fullName() {
      PackageInfo parent = this.parent();
      return parent != null ? parent.fullName() + "." + this.name() : this.name();
   }
}
