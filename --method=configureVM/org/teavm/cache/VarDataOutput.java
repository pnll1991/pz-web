package org.teavm.cache;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public class VarDataOutput implements Closeable {
   private static final int DATA = 127;
   private static final int NEXT = 128;
   private OutputStream output;

   public VarDataOutput(OutputStream output) {
      this.output = output;
   }

   public void writeUnsigned(int value) throws IOException {
      while ((value & 127) != value) {
         this.output.write(value & 127 | 128);
         value >>>= 7;
      }

      this.output.write(value);
   }

   public void writeSigned(int value) throws IOException {
      this.writeUnsigned(value < 0 ? ~value << 1 | 1 : value << 1);
   }

   public void writeUnsigned(long value) throws IOException {
      while ((value & 127L) != value) {
         this.output.write((int)(value & 127L) | 128);
         value >>>= 7;
      }

      this.output.write((int)value);
   }

   public void writeSigned(long value) throws IOException {
      this.writeUnsigned(value < 0L ? ~value << 1 | 1L : value << 1);
   }

   public void writeFloat(float value) throws IOException {
      if (value == 0.0F) {
         this.writeUnsigned(0);
      } else {
         int bits = Float.floatToRawIntBits(value);
         boolean sign = (bits & -2147483648) != 0;
         int exponent = bits >> 23 & 0xFF;
         int mantissa = bits & 8388607;
         if (sign) {
            mantissa |= 8388608;
         }

         exponent -= 127;
         this.writeUnsigned(1 + (exponent > 0 ? exponent << 1 : 1 | -exponent << 1));
         this.writeUnsigned(Integer.reverse(mantissa << 8));
      }
   }

   public void writeDouble(double value) throws IOException {
      if (value == 0.0) {
         this.writeUnsigned(0);
      } else {
         long bits = Double.doubleToRawLongBits(value);
         boolean sign = (bits & Long.MIN_VALUE) != 0L;
         int exponent = (int)(bits >> 52) & 2047;
         long mantissa = bits & 4503599627370495L;
         if (sign) {
            mantissa |= 4503599627370496L;
         }

         exponent -= 1023;
         this.writeUnsigned(1 + (exponent > 0 ? exponent << 1 : 1 | -exponent << 1));
         this.writeUnsigned(Long.reverse(mantissa << 11));
      }
   }

   public void write(String s) throws IOException {
      if (s == null) {
         this.writeUnsigned(0);
      } else {
         this.writeUnsigned(s.length() + 1);

         for (int i = 0; i < s.length(); i++) {
            this.writeUnsigned(s.charAt(i));
         }
      }
   }

   public void writeBytes(byte[] data) throws IOException {
      this.writeUnsigned(data.length);
      this.output.write(data);
   }

   @Override
   public void close() throws IOException {
      this.output.close();
   }
}
