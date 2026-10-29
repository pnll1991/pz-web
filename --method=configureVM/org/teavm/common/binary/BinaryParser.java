package org.teavm.common.binary;

import java.nio.charset.StandardCharsets;

public class BinaryParser {
   public byte[] data;
   public int ptr;

   public int readLEB() {
      int result = 0;
      int shift = 0;

      while (true) {
         byte b = this.data[this.ptr++];
         result |= (b & 127) << shift;
         if ((b & 128) == 0) {
            return result;
         }

         shift += 7;
      }
   }

   public int readSignedLEB() {
      int result = 0;
      int shift = 0;

      while (true) {
         byte b = this.data[this.ptr++];
         result |= (b & 127) << shift;
         if ((b & 128) == 0) {
            if ((b & 64) != 0) {
               result |= -1 << shift + 7;
            }

            return result;
         }

         shift += 7;
      }
   }

   public String readString() {
      int length = this.readLEB();
      String result = new String(this.data, this.ptr, length, StandardCharsets.UTF_8);
      this.ptr += length;
      return result;
   }
}
