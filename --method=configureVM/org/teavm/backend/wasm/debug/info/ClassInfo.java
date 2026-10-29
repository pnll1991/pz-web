package org.teavm.backend.wasm.debug.info;

public abstract class ClassInfo {
   public abstract PackageInfo pkg();

   public abstract String name();

   public String fullName() {
      PackageInfo pkg = this.pkg();
      return pkg != null ? pkg.fullName() + "." + this.name() : this.name();
   }
}
