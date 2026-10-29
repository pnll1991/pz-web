package org.teavm.cache;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class VarDataInput {
   private static final int DATA = 127;
   private static final int NEXT = 128;
   private InputStream input;

   public VarDataInput(InputStream input) {
      this.input = input;
   }

   public int readUnsigned() throws IOException {
      int value = 0;
      int pos = 0;

      int b;
      do {
         b = this.input.read();
         if (b < 0) {
            throw new EOFException();
         }

         value |= (b & 127) << pos;
         pos += 7;
      } while ((b & 128) != 0);

      return value;
   }

   public int readSigned() throws IOException {
      int value = this.readUnsigned();
      return (value & 1) == 0 ? value >>> 1 : ~(value >>> 1);
   }

   public long readUnsignedLong() throws IOException {
      long value = 0L;
      int pos = 0;

      int b;
      do {
         b = this.input.read();
         if (b < 0) {
            throw new EOFException();
         }

         value |= (b & 127L) << pos;
         pos += 7;
      } while ((b & 128) != 0);

      return value;
   }

   public long readSignedLong() throws IOException {
      long value = this.readUnsignedLong();
      return (value & 1L) == 0L ? value >>> 1 : ~(value >>> 1);
   }

   public float readFloat() throws IOException {
      int exponent = this.readUnsigned();
      if (exponent == 0) {
         return 0.0F;
      }

      exponent--;
      exponent = (exponent & 1) == 0 ? exponent >>> 1 : -(exponent >>> 1);
      exponent += 127;
      int mantissa = Integer.reverse(this.readUnsigned()) >>> 8;
      boolean sign = (mantissa & 8388608) != 0;
      int bits = mantissa & 8388607;
      bits |= exponent << 23;
      if (sign) {
         bits |= Integer.MIN_VALUE;
      }

      return Float.intBitsToFloat(bits);
   }

   public double readDouble() throws IOException {
      int exponent = this.readUnsigned();
      if (exponent == 0) {
         return 0.0;
      }

      exponent--;
      exponent = (exponent & 1) == 0 ? exponent >>> 1 : -(exponent >>> 1);
      exponent += 1023;
      long mantissa = Long.reverse(this.readUnsignedLong()) >>> 11;
      boolean sign = (mantissa & 4503599627370496L) != 0L;
      long bits = mantissa & 4503599627370495L;
      bits |= (long)exponent << 52;
      if (sign) {
         bits |= Long.MIN_VALUE;
      }

      return Double.longBitsToDouble(bits);
   }

   public String read() throws IOException {
      int sz = this.readUnsigned();
      if (sz == 0) {
         return null;
      }

      char[] chars = new char[--sz];

      for (int i = 0; i < sz; i++) {
         chars[i] = (char)this.readUnsigned();
      }

      return new String(chars);
   }

   public byte[] readBytes() throws IOException {
      byte[] buf = new byte[this.readUnsigned()];
      int off = 0;

      while (true) {
         int toRead = buf.length - off;
         if (toRead == 0) {
            break;
         }

         int read = this.input.read(buf, off, toRead);
         if (read < 0) {
            throw new IllegalStateException();
         }

         if (read == toRead) {
            break;
         }

         off += read;
      }

      return buf;
   }
}
