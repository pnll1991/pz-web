package com.jcraft.jzlib;

public final class CRC32 implements Checksum {
   private int v = 0;
   private static int[] crc_table = null;
   private static final int GF2_DIM = 32;

   @Override
   public void update(byte[] buf, int index, int len) {
      int c = ~this.v;

      while (--len >= 0) {
         c = crc_table[(c ^ buf[index++]) & 0xFF] ^ c >>> 8;
      }

      this.v = ~c;
   }

   @Override
   public void reset() {
      this.v = 0;
   }

   @Override
   public void reset(long vv) {
      this.v = (int)(vv & 4294967295L);
   }

   @Override
   public long getValue() {
      return this.v & 4294967295L;
   }

   static long combine(long crc1, long crc2, long len2) {
      long[] even = new long[32];
      long[] odd = new long[32];
      if (len2 <= 0L) {
         return crc1;
      }

      odd[0] = 3988292384L;
      long row = 1L;

      for (int n = 1; n < 32; n++) {
         odd[n] = row;
         row <<= 1;
      }

      gf2_matrix_square(even, odd);
      gf2_matrix_square(odd, even);

      do {
         gf2_matrix_square(even, odd);
         if ((len2 & 1L) != 0L) {
            crc1 = gf2_matrix_times(even, crc1);
         }

         len2 >>= 1;
         if (len2 == 0L) {
            break;
         }

         gf2_matrix_square(odd, even);
         if ((len2 & 1L) != 0L) {
            crc1 = gf2_matrix_times(odd, crc1);
         }

         len2 >>= 1;
      } while (len2 != 0L);

      return crc1 ^ crc2;
   }

   private static long gf2_matrix_times(long[] mat, long vec) {
      long sum = 0L;

      for (int index = 0; vec != 0L; index++) {
         if ((vec & 1L) != 0L) {
            sum ^= mat[index];
         }

         vec >>= 1;
      }

      return sum;
   }

   static final void gf2_matrix_square(long[] square, long[] mat) {
      for (int n = 0; n < 32; n++) {
         square[n] = gf2_matrix_times(mat, mat[n]);
      }
   }

   public CRC32 copy() {
      CRC32 foo = new CRC32();
      foo.v = this.v;
      return foo;
   }

   public static int[] getCRC32Table() {
      int[] tmp = new int[crc_table.length];
      System.arraycopy(crc_table, 0, tmp, 0, tmp.length);
      return tmp;
   }

   static {
      crc_table = new int[256];

      for (int n = 0; n < 256; n++) {
         int c = n;
         int k = 8;

         while (--k >= 0) {
            if ((c & 1) != 0) {
               c = -306674912 ^ c >>> 1;
            } else {
               c >>>= 1;
            }
         }

         crc_table[n] = c;
      }
   }
}
