package org.teavm.backend.wasm.parser;

public interface SectionListener {
   default void sectionStart(int functionCount) {
   }

   default void sectionEnd() {
   }
}
