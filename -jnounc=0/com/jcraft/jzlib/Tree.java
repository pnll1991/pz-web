package com.jcraft.jzlib;

final class Tree {
   private static final int MAX_BITS = 15;
   private static final int BL_CODES = 19;
   private static final int D_CODES = 30;
   private static final int LITERALS = 256;
   private static final int LENGTH_CODES = 29;
   private static final int L_CODES = 286;
   private static final int HEAP_SIZE = 573;
   static final int MAX_BL_BITS = 7;
   static final int END_BLOCK = 256;
   static final int REP_3_6 = 16;
   static final int REPZ_3_10 = 17;
   static final int REPZ_11_138 = 18;
   static final int[] extra_lbits = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
   static final int[] extra_dbits = new int[]{0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};
   static final int[] extra_blbits = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 7};
   static final byte[] bl_order = new byte[]{16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
   static final int Buf_size = 16;
   static final int DIST_CODE_LEN = 512;
   static final byte[] _dist_code = new byte[]{
      0,
      1,
      2,
      3,
      4,
      4,
      5,
      5,
      6,
      6,
      6,
      6,
      7,
      7,
      7,
      7,
      8,
      8,
      8,
      8,
      8,
      8,
      8,
      8,
      9,
      9,
      9,
      9,
      9,
      9,
      9,
      9,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      10,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      11,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      12,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      13,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      14,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      15,
      0,
      0,
      16,
      17,
      18,
      18,
      19,
      19,
      20,
      20,
      20,
      20,
      21,
      21,
      21,
      21,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      28,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29,
      29
   };
   static final byte[] _length_code = new byte[]{
      0,
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      8,
      9,
      9,
      10,
      10,
      11,
      11,
      12,
      12,
      12,
      12,
      13,
      13,
      13,
      13,
      14,
      14,
      14,
      14,
      15,
      15,
      15,
      15,
      16,
      16,
      16,
      16,
      16,
      16,
      16,
      16,
      17,
      17,
      17,
      17,
      17,
      17,
      17,
      17,
      18,
      18,
      18,
      18,
      18,
      18,
      18,
      18,
      19,
      19,
      19,
      19,
      19,
      19,
      19,
      19,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      20,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      21,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      22,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      23,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      24,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      25,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      26,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      27,
      28
   };
   static final int[] base_length = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 0};
   static final int[] base_dist = new int[]{
      0, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576
   };
   short[] dyn_tree;
   int max_code;
   StaticTree stat_desc;

   static int d_code(int dist) {
      return dist < 256 ? _dist_code[dist] : _dist_code[256 + (dist >>> 7)];
   }

   void gen_bitlen(Deflate s) {
      short[] tree = this.dyn_tree;
      short[] stree = this.stat_desc.static_tree;
      int[] extra = this.stat_desc.extra_bits;
      int base = this.stat_desc.extra_base;
      int max_length = this.stat_desc.max_length;
      int overflow = 0;

      for (int bits = 0; bits <= 15; bits++) {
         s.bl_count[bits] = 0;
      }

      tree[s.heap[s.heap_max] * 2 + 1] = 0;

      int h;
      for (h = s.heap_max + 1; h < 573; h++) {
         int n = s.heap[h];
         int var15 = tree[tree[n * 2 + 1] * 2 + 1] + 1;
         if (var15 > max_length) {
            var15 = max_length;
            overflow++;
         }

         tree[n * 2 + 1] = (short)var15;
         if (n <= this.max_code) {
            s.bl_count[var15]++;
            int xbits = 0;
            if (n >= base) {
               xbits = extra[n - base];
            }

            short f = tree[n * 2];
            s.opt_len += f * (var15 + xbits);
            if (stree != null) {
               s.static_len = s.static_len + f * (stree[n * 2 + 1] + xbits);
            }
         }
      }

      if (overflow != 0) {
         do {
            int var16 = max_length - 1;

            while (s.bl_count[var16] == 0) {
               var16--;
            }

            s.bl_count[var16]--;
            s.bl_count[var16 + 1] = (short)(s.bl_count[var16 + 1] + 2);
            s.bl_count[max_length]--;
            overflow -= 2;
         } while (overflow > 0);

         for (int var17 = max_length; var17 != 0; var17--) {
            int n = s.bl_count[var17];

            while (n != 0) {
               int m = s.heap[--h];
               if (m <= this.max_code) {
                  if (tree[m * 2 + 1] != var17) {
                     s.opt_len = (int)(s.opt_len + ((long)var17 - tree[m * 2 + 1]) * tree[m * 2]);
                     tree[m * 2 + 1] = (short)var17;
                  }

                  n--;
               }
            }
         }
      }
   }

   void build_tree(Deflate s) {
      short[] tree = this.dyn_tree;
      short[] stree = this.stat_desc.static_tree;
      int elems = this.stat_desc.elems;
      int max_code = -1;
      s.heap_len = 0;
      s.heap_max = 573;

      for (int n = 0; n < elems; n++) {
         if (tree[n * 2] != 0) {
            int[] var10000 = s.heap;
            int var10001 = ++s.heap_len;
            max_code = n;
            var10000[var10001] = n;
            s.depth[n] = 0;
         } else {
            tree[n * 2 + 1] = 0;
         }
      }

      while (s.heap_len < 2) {
         int node = s.heap[++s.heap_len] = max_code < 2 ? ++max_code : 0;
         tree[node * 2] = 1;
         s.depth[node] = 0;
         s.opt_len--;
         if (stree != null) {
            s.static_len = s.static_len - stree[node * 2 + 1];
         }
      }

      this.max_code = max_code;

      for (int var9 = s.heap_len / 2; var9 >= 1; var9--) {
         s.pqdownheap(tree, var9);
      }

      int node = elems;

      do {
         int var10 = s.heap[1];
         s.heap[1] = s.heap[s.heap_len--];
         s.pqdownheap(tree, 1);
         int m = s.heap[1];
         s.heap[--s.heap_max] = var10;
         s.heap[--s.heap_max] = m;
         tree[node * 2] = (short)(tree[var10 * 2] + tree[m * 2]);
         s.depth[node] = (byte)(Math.max(s.depth[var10], s.depth[m]) + 1);
         tree[var10 * 2 + 1] = tree[m * 2 + 1] = (short)node;
         s.heap[1] = node++;
         s.pqdownheap(tree, 1);
      } while (s.heap_len >= 2);

      s.heap[--s.heap_max] = s.heap[1];
      this.gen_bitlen(s);
      gen_codes(tree, max_code, s.bl_count, s.next_code);
   }

   private static final void gen_codes(short[] tree, int max_code, short[] bl_count, short[] next_code) {
      short code = 0;
      next_code[0] = 0;

      for (int bits = 1; bits <= 15; bits++) {
         next_code[bits] = code = (short)(code + bl_count[bits - 1] << 1);
      }

      for (int n = 0; n <= max_code; n++) {
         int len = tree[n * 2 + 1];
         if (len != 0) {
            int var10001 = n * 2;
            next_code[len]++;
            tree[var10001] = (short)bi_reverse(next_code[len], len);
         }
      }
   }

   private static final int bi_reverse(int code, int len) {
      int res = 0;

      do {
         res |= code & 1;
         code >>>= 1;
         res <<= 1;
      } while (--len > 0);

      return res >>> 1;
   }
}
