package org.teavm.backend.wasm.model;

import java.util.Arrays;

public class WasmMemorySegment {
   private int offset;
   private byte[] data = new byte[0];

   public int getOffset() {
      return this.offset;
   }

   public void setOffset(int offset) {
      this.offset = offset;
   }

   public byte[] getData() {
      return (byte[])this.data.clone();
   }

   public void setData(byte[] data) {
      this.data = (byte[])data.clone();
   }

   public int getLength() {
      return this.data.length;
   }

   public void setLength(int value) {
      this.data = Arrays.copyOf(this.data, value);
   }

   public byte[] getData(int offset, int size) {
      return Arrays.copyOfRange(this.data, offset, offset + size);
   }
}
