package org.teavm.backend.wasm.runtime;

import org.teavm.interop.Import;
import org.teavm.interop.Intrinsified;

public class WasmGCSupport {
   private static int lastObjectId = 1831433054;

   private WasmGCSupport() {
   }

   public static NullPointerException npe() {
      return new NullPointerException();
   }

   public static ArrayIndexOutOfBoundsException aiiobe() {
      return new ArrayIndexOutOfBoundsException();
   }

   public static ClassCastException cce() {
      return new ClassCastException();
   }

   public static void throwCloneNotSupportedException() throws CloneNotSupportedException {
      throw new CloneNotSupportedException();
   }

   public static Object defaultClone(Object object) throws CloneNotSupportedException {
      throw new CloneNotSupportedException();
   }

   public static int nextObjectId() {
      int x = lastObjectId;
      x ^= x << 13;
      x ^= x >>> 17;
      x ^= x << 5;
      lastObjectId = x;
      return x;
   }

   @Import(name = "putcharStdout", module = "teavmConsole")
   public static native void putCharStdout(char var0);

   @Import(name = "putcharStderr", module = "teavmConsole")
   public static native void putCharStderr(char var0);

   public static char[] nextCharArray() {
      int length = nextLEB();
      char[] result = new char[length];
      int pos = 0;

      while (pos < length) {
         byte b = nextByte();
         if ((b & 128) == 0) {
            result[pos++] = (char)b;
         } else if ((b & 224) == 192) {
            byte b2 = nextByte();
            result[pos++] = (char)((b & 31) << 6 | b2 & 63);
         } else if ((b & 240) == 224) {
            byte b2 = nextByte();
            byte b3 = nextByte();
            char c = (char)((b & 15) << 12 | (b2 & 63) << 6 | b3 & 63);
            result[pos++] = c;
         } else if ((b & 248) == 240) {
            byte b2 = nextByte();
            byte b3 = nextByte();
            byte b4 = nextByte();
            int code = (b & 7) << 18 | (b2 & 63) << 12 | (b3 & 63) << 6 | b4 & 63;
            result[pos++] = Character.highSurrogate(code);
            result[pos++] = Character.lowSurrogate(code);
         }
      }

      return result;
   }

   private static int nextLEB() {
      int shift = 0;
      int result = 0;

      while (true) {
         byte b = nextByte();
         int digit = b & 127;
         result |= digit << shift;
         if ((b & 128) == 0) {
            return result;
         }

         shift += 7;
      }
   }

   @Intrinsified
   private static native byte nextByte();

   private static native void error();

   public static StringBuilder createStringBuilder() {
      return new StringBuilder();
   }

   public static String[] createStringArray(int size) {
      return new String[size];
   }

   public static void setToStringArray(String[] array, int index, String value) {
      array[index] = value;
   }
}
