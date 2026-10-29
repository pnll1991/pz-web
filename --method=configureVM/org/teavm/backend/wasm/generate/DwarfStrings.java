package org.teavm.backend.wasm.generate;

import java.nio.charset.StandardCharsets;
import org.teavm.common.binary.Blob;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public class DwarfStrings {
   final Blob blob = new Blob();
   private ObjectIntMap<String> offsets = new ObjectIntHashMap();

   public int stringRef(String s) {
      int ptr = this.offsets.getOrDefault(s, -1);
      if (ptr < 0) {
         ptr = this.blob.size();
         this.offsets.put(s, ptr);
         byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
         this.blob.write(bytes);
         this.blob.writeByte(0);
      }

      return ptr;
   }
}
