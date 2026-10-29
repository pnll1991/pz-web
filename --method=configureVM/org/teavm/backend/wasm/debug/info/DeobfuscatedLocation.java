package org.teavm.backend.wasm.debug.info;

public class DeobfuscatedLocation {
   public final FileInfo file;
   public final MethodInfo method;
   public final int line;

   public DeobfuscatedLocation(FileInfo file, MethodInfo method, int line) {
      this.file = file;
      this.method = method;
      this.line = line;
   }

   @Override
   public String toString() {
      String sourceLocation = this.file != null && this.line >= 0 ? this.file.name() + ":" + this.line : "Unknow source";
      return "   at " + this.method.cls().name() + "." + this.method.name() + "(" + sourceLocation + ")";
   }
}
