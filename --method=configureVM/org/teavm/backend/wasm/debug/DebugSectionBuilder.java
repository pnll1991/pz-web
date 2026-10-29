package org.teavm.backend.wasm.debug;

import org.teavm.common.binary.BinaryDataConsumer;
import org.teavm.common.binary.Blob;

public class DebugSectionBuilder {
   private String name;
   protected Blob blob = new Blob();

   protected DebugSectionBuilder(String name) {
      this.name = name;
   }

   public void read(BinaryDataConsumer consumer) {
      this.blob.newReader(consumer).readRemaining();
   }

   public String name() {
      return this.name;
   }

   public byte[] build() {
      return this.blob.toArray();
   }

   public boolean isEmpty() {
      return this.blob.size() == 0;
   }
}
