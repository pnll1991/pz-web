package org.teavm.backend.wasm.parser;

public interface CodeSectionListener extends SectionListener {
   default boolean functionStart(int index, int size) {
      return false;
   }

   default void localsStart(int count) {
   }

   default void local(int start, int count, WasmHollowType type) {
   }

   default CodeListener code() {
      return null;
   }

   default void functionEnd() {
   }
}
