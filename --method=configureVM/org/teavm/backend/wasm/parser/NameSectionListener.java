package org.teavm.backend.wasm.parser;

public interface NameSectionListener {
   default NameMapListener functions() {
      return null;
   }

   default NameIndirectMapListener locals() {
      return null;
   }

   default NameMapListener types() {
      return null;
   }

   default NameMapListener globals() {
      return null;
   }

   default NameIndirectMapListener fields() {
      return null;
   }
}
