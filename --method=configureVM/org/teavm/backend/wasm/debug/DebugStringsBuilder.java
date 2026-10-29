package org.teavm.backend.wasm.debug;

import java.nio.charset.StandardCharsets;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public class DebugStringsBuilder extends DebugSectionBuilder implements DebugStrings {
   private ObjectIntMap<String> strings = new ObjectIntHashMap();

   public DebugStringsBuilder() {
      super("teavm_str");
   }

   @Override
   public int stringPtr(String str) {
      int result = this.strings.getOrDefault(str, -1);
      if (result < 0) {
         result = this.strings.size();
         this.strings.put(str, result);
         this.blob.writeLEB(str.length());
         this.blob.write(str.getBytes(StandardCharsets.UTF_8));
      }

      return result;
   }
}
