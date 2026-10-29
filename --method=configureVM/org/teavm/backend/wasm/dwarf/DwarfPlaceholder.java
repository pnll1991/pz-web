package org.teavm.backend.wasm.dwarf;

import java.util.ArrayList;
import java.util.List;
import org.teavm.common.binary.Marker;

public class DwarfPlaceholder {
   int ptr = -1;
   final int size;
   List<DwarfPlaceholder.ForwardRef> forwardReferences;

   DwarfPlaceholder(int size) {
      this.size = size;
   }

   void addForwardRef(DwarfPlaceholderWriter writer, Marker marker) {
      if (this.forwardReferences == null) {
         this.forwardReferences = new ArrayList<>();
      }

      this.forwardReferences.add(new DwarfPlaceholder.ForwardRef(writer, marker));
   }

   static class ForwardRef {
      final DwarfPlaceholderWriter writer;
      final Marker marker;

      ForwardRef(DwarfPlaceholderWriter writer, Marker marker) {
         this.writer = writer;
         this.marker = marker;
      }
   }
}
