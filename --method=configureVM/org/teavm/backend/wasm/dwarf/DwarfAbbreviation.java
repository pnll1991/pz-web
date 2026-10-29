package org.teavm.backend.wasm.dwarf;

import java.util.function.Consumer;
import org.teavm.common.binary.Blob;

public class DwarfAbbreviation {
   int tag;
   boolean hasChildren;
   Consumer<Blob> writer;
   int index;
   int count;

   DwarfAbbreviation(int tag, boolean hasChildren, Consumer<Blob> writer) {
      this.tag = tag;
      this.hasChildren = hasChildren;
      this.writer = writer;
   }
}
