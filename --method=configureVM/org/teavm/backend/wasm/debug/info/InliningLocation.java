package org.teavm.backend.wasm.debug.info;

public class InliningLocation {
   private Location location;
   private MethodInfo method;

   public InliningLocation(Location location, MethodInfo method) {
      this.location = location;
      this.method = method;
   }

   public Location location() {
      return this.location;
   }

   public MethodInfo method() {
      return this.method;
   }
}
