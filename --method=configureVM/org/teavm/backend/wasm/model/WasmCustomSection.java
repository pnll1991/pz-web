package org.teavm.backend.wasm.model;

public class WasmCustomSection {
   private String name;
   private byte[] data;
   WasmModule module;

   public WasmCustomSection(String name, byte[] data) {
      this.name = name;
      this.data = data;
   }

   public String getName() {
      return this.name;
   }

   public byte[] getData() {
      return this.data;
   }
}
