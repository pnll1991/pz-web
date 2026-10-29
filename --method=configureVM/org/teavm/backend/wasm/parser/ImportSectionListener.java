package org.teavm.backend.wasm.parser;

public interface ImportSectionListener {
   default void startEntry(String module, String name) {
   }

   default void function(int typeIndex) {
   }

   default void global(WasmHollowType type, boolean mutable) {
   }

   default void memory(int minSize, int maxSize) {
   }

   default void endEntry() {
   }
}
