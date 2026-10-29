package com.jcraft.jzlib;

public final class Adler32 implements Checksum {
   private static final int BASE = 65521;
   private static final int NMAX = 5552;
   private long s1 = 1L;
   private long s2 = 0L;

   @Override
   public void reset(long init) {
      this.s1 = init & 65535L;
      this.s2 = init >> 16 & 65535L;
   }

   @Override
   public void reset() {
      this.s1 = 1L;
      this.s2 = 0L;
   }

   @Override
   public long getValue() {
      return this.s2 << 16 | this.s1;
   }

   @Override
   public void update(byte[] buf, int index, int len) {
      if (len == 1) {
         this.s1 = this.s1 + (buf[index++] & 0xFF);
         this.s2 = this.s2 + this.s1;
         this.s1 %= 65521L;
         this.s2 %= 65521L;
      } else {
         int len1 = len / 5552;
         int len2 = len % 5552;

         while (len1-- > 0) {
            int k = 5552;
            len -= k;

            while (k-- > 0) {
               this.s1 = this.s1 + (buf[index++] & 0xFF);
               this.s2 = this.s2 + this.s1;
            }

            this.s1 %= 65521L;
            this.s2 %= 65521L;
         }

         int k = len2;
         len -= k;

         while (k-- > 0) {
            this.s1 = this.s1 + (buf[index++] & 0xFF);
            this.s2 = this.s2 + this.s1;
         }

         this.s1 %= 65521L;
         this.s2 %= 65521L;
      }
   }

   public Adler32 copy() {
      Adler32 foo = new Adler32();
      foo.s1 = this.s1;
      foo.s2 = this.s2;
      return foo;
   }

   static long combine(long adler1, long adler2, long len2) {
      long BASEL = 65521L;
      long rem = len2 % BASEL;
      long sum1 = adler1 & 65535L;
      long sum2 = rem * sum1;
      sum2 %= BASEL;
      sum1 += (adler2 & 65535L) + BASEL - 1L;
      sum2 += (adler1 >> 16 & 65535L) + (adler2 >> 16 & 65535L) + BASEL - rem;
      if (sum1 >= BASEL) {
         sum1 -= BASEL;
      }

      if (sum1 >= BASEL) {
         sum1 -= BASEL;
      }

      if (sum2 >= BASEL << 1) {
         sum2 -= BASEL << 1;
      }

      if (sum2 >= BASEL) {
         sum2 -= BASEL;
      }

      return sum1 | sum2 << 16;
   }
}
