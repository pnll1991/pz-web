package org.teavm.backend.wasm.debug.info;

public class InstructionLocation {
   private int address;
   private Location location;

   public InstructionLocation(int address, Location location) {
      this.address = address;
      this.location = location;
   }

   public int address() {
      return this.address;
   }

   public Location location() {
      return this.location;
   }
}
