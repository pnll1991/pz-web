package org.teavm.backend.wasm.model;

public abstract class WasmEntity {
   int index;
   int indexInCollection;
   WasmCollection<?> collection;

   boolean isImported() {
      return false;
   }
}
