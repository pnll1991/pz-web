package com.jcraft.jzlib;

final class InfBlocks {
   private static final int MANY = 1440;
   private static final int[] inflate_mask = new int[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767, 65535};
   static final int[] border = new int[]{16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
   private static final int Z_OK = 0;
   private static final int Z_STREAM_END = 1;
   private static final int Z_NEED_DICT = 2;
   private static final int Z_ERRNO = -1;
   private static final int Z_STREAM_ERROR = -2;
   private static final int Z_DATA_ERROR = -3;
   private static final int Z_MEM_ERROR = -4;
   private static final int Z_BUF_ERROR = -5;
   private static final int Z_VERSION_ERROR = -6;
   private static final int TYPE = 0;
   private static final int LENS = 1;
   private static final int STORED = 2;
   private static final int TABLE = 3;
   private static final int BTREE = 4;
   private static final int DTREE = 5;
   private static final int CODES = 6;
   private static final int DRY = 7;
   private static final int DONE = 8;
   private static final int BAD = 9;
   int mode;
   int left;
   int table;
   int index;
   int[] blens;
   int[] bb = new int[1];
   int[] tb = new int[1];
   int[] bl = new int[1];
   int[] bd = new int[1];
   int[][] tl = new int[1][];
   int[][] td = new int[1][];
   int[] tli = new int[1];
   int[] tdi = new int[1];
   private final InfCodes codes;
   int last;
   int bitk;
   int bitb;
   int[] hufts;
   byte[] window;
   int end;
   int read;
   int write;
   private boolean check;
   private final InfTree inftree = new InfTree();
   private final ZStream z;

   InfBlocks(ZStream z, int w) {
      this.z = z;
      this.codes = new InfCodes(this.z, this);
      this.hufts = new int[4320];
      this.window = new byte[w];
      this.end = w;
      this.check = z.istate.wrap != 0;
      this.mode = 0;
      this.reset();
   }

   void reset() {
      if (this.mode != 4 && this.mode == 5) {
      }

      if (this.mode == 6) {
         this.codes.free(this.z);
      }

      this.mode = 0;
      this.bitk = 0;
      this.bitb = 0;
      this.read = this.write = 0;
      if (this.check) {
         this.z.adler.reset();
      }
   }

   int proc(int r) {
      int p = this.z.next_in_index;
      int n = this.z.avail_in;
      int b = this.bitb;
      int k = this.bitk;
      int q = this.write;
      int m = q < this.read ? this.read - q - 1 : this.end - q;

      label321:
      while (true) {
         label314:
         while (true) {
            label312: {
               label384: {
                  switch (this.mode) {
                     case 0:
                        while (k < 3) {
                           if (n == 0) {
                              this.bitb = b;
                              this.bitk = k;
                              this.z.avail_in = n;
                              this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                              this.z.next_in_index = p;
                              this.write = q;
                              return this.inflate_flush(r);
                           }

                           r = 0;
                           n--;
                           b |= (this.z.next_in[p++] & 255) << k;
                           k += 8;
                        }

                        int t = b & 7;
                        this.last = t & 1;
                        switch (t >>> 1) {
                           case 0:
                              b >>>= 3;
                              k -= 3;
                              t = k & 7;
                              b >>>= t;
                              k -= t;
                              this.mode = 1;
                              continue;
                           case 1:
                              InfTree.inflate_trees_fixed(this.bl, this.bd, this.tl, this.td, this.z);
                              this.codes.init(this.bl[0], this.bd[0], this.tl[0], 0, this.td[0], 0);
                              b >>>= 3;
                              k -= 3;
                              this.mode = 6;
                              continue;
                           case 2:
                              b >>>= 3;
                              k -= 3;
                              this.mode = 3;
                              continue;
                           case 3:
                              b >>>= 3;
                              k -= 3;
                              this.mode = 9;
                              this.z.msg = "invalid block type";
                              int var24 = -3;
                              this.bitb = b;
                              this.bitk = k;
                              this.z.avail_in = n;
                              this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                              this.z.next_in_index = p;
                              this.write = q;
                              return this.inflate_flush(var24);
                           default:
                              continue;
                        }
                     case 1:
                        while (k < 32) {
                           if (n == 0) {
                              this.bitb = b;
                              this.bitk = k;
                              this.z.avail_in = n;
                              this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                              this.z.next_in_index = p;
                              this.write = q;
                              return this.inflate_flush(r);
                           }

                           r = 0;
                           n--;
                           b |= (this.z.next_in[p++] & 255) << k;
                           k += 8;
                        }

                        if ((~b >>> 16 & 65535) != (b & 65535)) {
                           this.mode = 9;
                           this.z.msg = "invalid stored block lengths";
                           int var23 = -3;
                           this.bitb = b;
                           this.bitk = k;
                           this.z.avail_in = n;
                           this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                           this.z.next_in_index = p;
                           this.write = q;
                           return this.inflate_flush(var23);
                        }

                        this.left = b & 65535;
                        k = 0;
                        b = 0;
                        this.mode = this.left != 0 ? 2 : (this.last != 0 ? 7 : 0);
                        continue;
                     case 2:
                        if (n == 0) {
                           this.bitb = b;
                           this.bitk = k;
                           this.z.avail_in = n;
                           this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                           this.z.next_in_index = p;
                           this.write = q;
                           return this.inflate_flush(r);
                        }

                        if (m == 0) {
                           if (q == this.end && this.read != 0) {
                              q = 0;
                              m = q < this.read ? this.read - q - 1 : this.end - q;
                           }

                           if (m == 0) {
                              this.write = q;
                              r = this.inflate_flush(r);
                              q = this.write;
                              m = q < this.read ? this.read - q - 1 : this.end - q;
                              if (q == this.end && this.read != 0) {
                                 q = 0;
                                 m = q < this.read ? this.read - q - 1 : this.end - q;
                              }

                              if (m == 0) {
                                 this.bitb = b;
                                 this.bitk = k;
                                 this.z.avail_in = n;
                                 this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                                 this.z.next_in_index = p;
                                 this.write = q;
                                 return this.inflate_flush(r);
                              }
                           }
                        }

                        r = 0;
                        int var33 = this.left;
                        if (var33 > n) {
                           var33 = n;
                        }

                        if (var33 > m) {
                           var33 = m;
                        }

                        System.arraycopy(this.z.next_in, p, this.window, q, var33);
                        p += var33;
                        n -= var33;
                        q += var33;
                        m -= var33;
                        if ((this.left -= var33) == 0) {
                           this.mode = this.last != 0 ? 7 : 0;
                        }
                        continue;
                     case 3:
                        while (k < 14) {
                           if (n == 0) {
                              this.bitb = b;
                              this.bitk = k;
                              this.z.avail_in = n;
                              this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                              this.z.next_in_index = p;
                              this.write = q;
                              return this.inflate_flush(r);
                           }

                           r = 0;
                           n--;
                           b |= (this.z.next_in[p++] & 255) << k;
                           k += 8;
                        }

                        int t;
                        this.table = t = b & 16383;
                        if ((t & 31) > 29 || (t >> 5 & 31) > 29) {
                           this.mode = 9;
                           this.z.msg = "too many length or distance symbols";
                           int var21 = -3;
                           this.bitb = b;
                           this.bitk = k;
                           this.z.avail_in = n;
                           this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                           this.z.next_in_index = p;
                           this.write = q;
                           return this.inflate_flush(var21);
                        }

                        t = 258 + (t & 31) + (t >> 5 & 31);
                        if (this.blens != null && this.blens.length >= t) {
                           for (int i = 0; i < t; i++) {
                              this.blens[i] = 0;
                           }
                        } else {
                           this.blens = new int[t];
                        }

                        b >>>= 14;
                        k -= 14;
                        this.index = 0;
                        this.mode = 4;
                     case 4:
                        break;
                     case 5:
                        break label384;
                     case 6:
                        break label312;
                     case 7:
                        break label314;
                     case 8:
                        break label321;
                     case 9:
                        int var14 = -3;
                        this.bitb = b;
                        this.bitk = k;
                        this.z.avail_in = n;
                        this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                        this.z.next_in_index = p;
                        this.write = q;
                        return this.inflate_flush(var14);
                     default:
                        int var13 = -2;
                        this.bitb = b;
                        this.bitk = k;
                        this.z.avail_in = n;
                        this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                        this.z.next_in_index = p;
                        this.write = q;
                        return this.inflate_flush(var13);
                  }

                  while (this.index < 4 + (this.table >>> 10)) {
                     while (k < 3) {
                        if (n == 0) {
                           this.bitb = b;
                           this.bitk = k;
                           this.z.avail_in = n;
                           this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                           this.z.next_in_index = p;
                           this.write = q;
                           return this.inflate_flush(r);
                        }

                        r = 0;
                        n--;
                        b |= (this.z.next_in[p++] & 255) << k;
                        k += 8;
                     }

                     this.blens[border[this.index++]] = b & 7;
                     b >>>= 3;
                     k -= 3;
                  }

                  while (this.index < 19) {
                     this.blens[border[this.index++]] = 0;
                  }

                  this.bb[0] = 7;
                  int var26 = this.inftree.inflate_trees_bits(this.blens, this.bb, this.tb, this.hufts, this.z);
                  if (var26 != 0) {
                     r = var26;
                     if (r == -3) {
                        this.blens = null;
                        this.mode = 9;
                     }

                     this.bitb = b;
                     this.bitk = k;
                     this.z.avail_in = n;
                     this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                     this.z.next_in_index = p;
                     this.write = q;
                     return this.inflate_flush(r);
                  }

                  this.index = 0;
                  this.mode = 5;
               }

               while (true) {
                  int var27 = this.table;
                  if (this.index >= 258 + (var27 & 31) + (var27 >> 5 & 31)) {
                     this.tb[0] = -1;
                     this.bl[0] = 9;
                     this.bd[0] = 6;
                     var27 = this.table;
                     var27 = this.inftree
                        .inflate_trees_dynamic(257 + (var27 & 31), 1 + (var27 >> 5 & 31), this.blens, this.bl, this.bd, this.tli, this.tdi, this.hufts, this.z);
                     if (var27 != 0) {
                        if (var27 == -3) {
                           this.blens = null;
                           this.mode = 9;
                        }

                        r = var27;
                        this.bitb = b;
                        this.bitk = k;
                        this.z.avail_in = n;
                        this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                        this.z.next_in_index = p;
                        this.write = q;
                        return this.inflate_flush(r);
                     }

                     this.codes.init(this.bl[0], this.bd[0], this.hufts, this.tli[0], this.hufts, this.tdi[0]);
                     this.mode = 6;
                     break;
                  }

                  for (var27 = this.bb[0]; k < var27; k += 8) {
                     if (n == 0) {
                        this.bitb = b;
                        this.bitk = k;
                        this.z.avail_in = n;
                        this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                        this.z.next_in_index = p;
                        this.write = q;
                        return this.inflate_flush(r);
                     }

                     r = 0;
                     n--;
                     b |= (this.z.next_in[p++] & 255) << k;
                  }

                  if (this.tb[0] == -1) {
                  }

                  var27 = this.hufts[(this.tb[0] + (b & inflate_mask[var27])) * 3 + 1];
                  int c = this.hufts[(this.tb[0] + (b & inflate_mask[var27])) * 3 + 2];
                  if (c < 16) {
                     b >>>= var27;
                     k -= var27;
                     this.blens[this.index++] = c;
                  } else {
                     int i = c == 18 ? 7 : c - 14;
                     int j = c == 18 ? 11 : 3;

                     while (k < var27 + i) {
                        if (n == 0) {
                           this.bitb = b;
                           this.bitk = k;
                           this.z.avail_in = n;
                           this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                           this.z.next_in_index = p;
                           this.write = q;
                           return this.inflate_flush(r);
                        }

                        r = 0;
                        n--;
                        b |= (this.z.next_in[p++] & 255) << k;
                        k += 8;
                     }

                     b >>>= var27;
                     k -= var27;
                     j += b & inflate_mask[i];
                     b >>>= i;
                     k -= i;
                     i = this.index;
                     var27 = this.table;
                     if (i + j > 258 + (var27 & 31) + (var27 >> 5 & 31) || c == 16 && i < 1) {
                        this.blens = null;
                        this.mode = 9;
                        this.z.msg = "invalid bit length repeat";
                        int var15 = -3;
                        this.bitb = b;
                        this.bitk = k;
                        this.z.avail_in = n;
                        this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                        this.z.next_in_index = p;
                        this.write = q;
                        return this.inflate_flush(var15);
                     }

                     c = c == 16 ? this.blens[i - 1] : 0;

                     do {
                        this.blens[i++] = c;
                     } while (--j != 0);

                     this.index = i;
                  }
               }
            }

            this.bitb = b;
            this.bitk = k;
            this.z.avail_in = n;
            this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
            this.z.next_in_index = p;
            this.write = q;
            if ((r = this.codes.proc(r)) != 1) {
               return this.inflate_flush(r);
            }

            r = 0;
            this.codes.free(this.z);
            p = this.z.next_in_index;
            n = this.z.avail_in;
            b = this.bitb;
            k = this.bitk;
            q = this.write;
            m = q < this.read ? this.read - q - 1 : this.end - q;
            if (this.last != 0) {
               this.mode = 7;
               break;
            }

            this.mode = 0;
         }

         this.write = q;
         r = this.inflate_flush(r);
         q = this.write;
         if (q < this.read) {
            int var10000 = this.read - q - 1;
         } else {
            int var45 = this.end - q;
         }

         if (this.read != this.write) {
            this.bitb = b;
            this.bitk = k;
            this.z.avail_in = n;
            this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
            this.z.next_in_index = p;
            this.write = q;
            return this.inflate_flush(r);
         }

         this.mode = 8;
         break;
      }

      int var18 = 1;
      this.bitb = b;
      this.bitk = k;
      this.z.avail_in = n;
      this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
      this.z.next_in_index = p;
      this.write = q;
      return this.inflate_flush(var18);
   }

   void free() {
      this.reset();
      this.window = null;
      this.hufts = null;
   }

   void set_dictionary(byte[] d, int start, int n) {
      System.arraycopy(d, start, this.window, 0, n);
      this.read = this.write = n;
   }

   int sync_point() {
      return this.mode == 1 ? 1 : 0;
   }

   int inflate_flush(int r) {
      int p = this.z.next_out_index;
      int q = this.read;
      int n = (q <= this.write ? this.write : this.end) - q;
      if (n > this.z.avail_out) {
         n = this.z.avail_out;
      }

      if (n != 0 && r == -5) {
         r = 0;
      }

      this.z.avail_out -= n;
      this.z.total_out += n;
      if (this.check && n > 0) {
         this.z.adler.update(this.window, q, n);
      }

      System.arraycopy(this.window, q, this.z.next_out, p, n);
      p += n;
      q += n;
      if (q == this.end) {
         int var8 = 0;
         if (this.write == this.end) {
            this.write = 0;
         }

         n = this.write - var8;
         if (n > this.z.avail_out) {
            n = this.z.avail_out;
         }

         if (n != 0 && r == -5) {
            r = 0;
         }

         this.z.avail_out -= n;
         this.z.total_out += n;
         if (this.check && n > 0) {
            this.z.adler.update(this.window, var8, n);
         }

         System.arraycopy(this.window, var8, this.z.next_out, p, n);
         p += n;
         q = var8 + n;
      }

      this.z.next_out_index = p;
      this.read = q;
      return r;
   }
}
