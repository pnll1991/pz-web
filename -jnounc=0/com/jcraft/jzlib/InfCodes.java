package com.jcraft.jzlib;

final class InfCodes {
   private static final int[] inflate_mask = new int[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767, 65535};
   private static final int Z_OK = 0;
   private static final int Z_STREAM_END = 1;
   private static final int Z_NEED_DICT = 2;
   private static final int Z_ERRNO = -1;
   private static final int Z_STREAM_ERROR = -2;
   private static final int Z_DATA_ERROR = -3;
   private static final int Z_MEM_ERROR = -4;
   private static final int Z_BUF_ERROR = -5;
   private static final int Z_VERSION_ERROR = -6;
   private static final int START = 0;
   private static final int LEN = 1;
   private static final int LENEXT = 2;
   private static final int DIST = 3;
   private static final int DISTEXT = 4;
   private static final int COPY = 5;
   private static final int LIT = 6;
   private static final int WASH = 7;
   private static final int END = 8;
   private static final int BADCODE = 9;
   int mode;
   int len;
   int[] tree;
   int tree_index = 0;
   int need;
   int lit;
   int get;
   int dist;
   byte lbits;
   byte dbits;
   int[] ltree;
   int ltree_index;
   int[] dtree;
   int dtree_index;
   private final ZStream z;
   private final InfBlocks s;

   InfCodes(ZStream z, InfBlocks s) {
      this.z = z;
      this.s = s;
   }

   void init(int bl, int bd, int[] tl, int tl_index, int[] td, int td_index) {
      this.mode = 0;
      this.lbits = (byte)bl;
      this.dbits = (byte)bd;
      this.ltree = tl;
      this.ltree_index = tl_index;
      this.dtree = td;
      this.dtree_index = td_index;
      this.tree = null;
   }

   int proc(int r) {
      int b = 0;
      int k = 0;
      int p = 0;
      p = this.z.next_in_index;
      int n = this.z.avail_in;
      b = this.s.bitb;
      k = this.s.bitk;
      int q = this.s.write;
      int m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;

      while (true) {
         switch (this.mode) {
            case 0:
               if (m >= 258 && n >= 10) {
                  this.s.bitb = b;
                  this.s.bitk = k;
                  this.z.avail_in = n;
                  this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                  this.z.next_in_index = p;
                  this.s.write = q;
                  r = this.inflate_fast(this.lbits, this.dbits, this.ltree, this.ltree_index, this.dtree, this.dtree_index, this.s, this.z);
                  p = this.z.next_in_index;
                  n = this.z.avail_in;
                  b = this.s.bitb;
                  k = this.s.bitk;
                  q = this.s.write;
                  m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;
                  if (r != 0) {
                     this.mode = r == 1 ? 7 : 9;
                     break;
                  }
               }

               this.need = this.lbits;
               this.tree = this.ltree;
               this.tree_index = this.ltree_index;
               this.mode = 1;
            case 1:
               int j;
               for (j = this.need; k < j; k += 8) {
                  if (n == 0) {
                     this.s.bitb = b;
                     this.s.bitk = k;
                     this.z.avail_in = n;
                     this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                     this.z.next_in_index = p;
                     this.s.write = q;
                     return this.s.inflate_flush(r);
                  }

                  r = 0;
                  n--;
                  b |= (this.z.next_in[p++] & 255) << k;
               }

               int tindex = (this.tree_index + (b & inflate_mask[j])) * 3;
               b >>>= this.tree[tindex + 1];
               k -= this.tree[tindex + 1];
               int e = this.tree[tindex];
               if (e == 0) {
                  this.lit = this.tree[tindex + 2];
                  this.mode = 6;
               } else if ((e & 16) != 0) {
                  this.get = e & 15;
                  this.len = this.tree[tindex + 2];
                  this.mode = 2;
               } else if ((e & 64) == 0) {
                  this.need = e;
                  this.tree_index = tindex / 3 + this.tree[tindex + 2];
               } else {
                  if ((e & 32) == 0) {
                     this.mode = 9;
                     this.z.msg = "invalid literal/length code";
                     int var19 = -3;
                     this.s.bitb = b;
                     this.s.bitk = k;
                     this.z.avail_in = n;
                     this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                     this.z.next_in_index = p;
                     this.s.write = q;
                     return this.s.inflate_flush(var19);
                  }

                  this.mode = 7;
               }
               break;
            case 2:
               int var20;
               for (var20 = this.get; k < var20; k += 8) {
                  if (n == 0) {
                     this.s.bitb = b;
                     this.s.bitk = k;
                     this.z.avail_in = n;
                     this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                     this.z.next_in_index = p;
                     this.s.write = q;
                     return this.s.inflate_flush(r);
                  }

                  r = 0;
                  n--;
                  b |= (this.z.next_in[p++] & 255) << k;
               }

               this.len = this.len + (b & inflate_mask[var20]);
               b >>= var20;
               k -= var20;
               this.need = this.dbits;
               this.tree = this.dtree;
               this.tree_index = this.dtree_index;
               this.mode = 3;
            case 3:
               int var21;
               for (var21 = this.need; k < var21; k += 8) {
                  if (n == 0) {
                     this.s.bitb = b;
                     this.s.bitk = k;
                     this.z.avail_in = n;
                     this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                     this.z.next_in_index = p;
                     this.s.write = q;
                     return this.s.inflate_flush(r);
                  }

                  r = 0;
                  n--;
                  b |= (this.z.next_in[p++] & 255) << k;
               }

               int tindex = (this.tree_index + (b & inflate_mask[var21])) * 3;
               b >>= this.tree[tindex + 1];
               k -= this.tree[tindex + 1];
               int e = this.tree[tindex];
               if ((e & 16) != 0) {
                  this.get = e & 15;
                  this.dist = this.tree[tindex + 2];
                  this.mode = 4;
               } else {
                  if ((e & 64) != 0) {
                     this.mode = 9;
                     this.z.msg = "invalid distance code";
                     int var18 = -3;
                     this.s.bitb = b;
                     this.s.bitk = k;
                     this.z.avail_in = n;
                     this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                     this.z.next_in_index = p;
                     this.s.write = q;
                     return this.s.inflate_flush(var18);
                  }

                  this.need = e;
                  this.tree_index = tindex / 3 + this.tree[tindex + 2];
               }
               break;
            case 4:
               int j;
               for (j = this.get; k < j; k += 8) {
                  if (n == 0) {
                     this.s.bitb = b;
                     this.s.bitk = k;
                     this.z.avail_in = n;
                     this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                     this.z.next_in_index = p;
                     this.s.write = q;
                     return this.s.inflate_flush(r);
                  }

                  r = 0;
                  n--;
                  b |= (this.z.next_in[p++] & 255) << k;
               }

               this.dist = this.dist + (b & inflate_mask[j]);
               b >>= j;
               k -= j;
               this.mode = 5;
            case 5:
               int f = q - this.dist;

               while (f < 0) {
                  f += this.s.end;
               }

               for (; this.len != 0; this.len--) {
                  if (m == 0) {
                     if (q == this.s.end && this.s.read != 0) {
                        q = 0;
                        m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;
                     }

                     if (m == 0) {
                        this.s.write = q;
                        r = this.s.inflate_flush(r);
                        q = this.s.write;
                        m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;
                        if (q == this.s.end && this.s.read != 0) {
                           q = 0;
                           m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;
                        }

                        if (m == 0) {
                           this.s.bitb = b;
                           this.s.bitk = k;
                           this.z.avail_in = n;
                           this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                           this.z.next_in_index = p;
                           this.s.write = q;
                           return this.s.inflate_flush(r);
                        }
                     }
                  }

                  this.s.window[q++] = this.s.window[f++];
                  m--;
                  if (f == this.s.end) {
                     f = 0;
                  }
               }

               this.mode = 0;
               break;
            case 6:
               if (m == 0) {
                  if (q == this.s.end && this.s.read != 0) {
                     q = 0;
                     m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;
                  }

                  if (m == 0) {
                     this.s.write = q;
                     r = this.s.inflate_flush(r);
                     q = this.s.write;
                     m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;
                     if (q == this.s.end && this.s.read != 0) {
                        q = 0;
                        m = q < this.s.read ? this.s.read - q - 1 : this.s.end - q;
                     }

                     if (m == 0) {
                        this.s.bitb = b;
                        this.s.bitk = k;
                        this.z.avail_in = n;
                        this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                        this.z.next_in_index = p;
                        this.s.write = q;
                        return this.s.inflate_flush(r);
                     }
                  }
               }

               r = 0;
               this.s.window[q++] = (byte)this.lit;
               m--;
               this.mode = 0;
               break;
            case 7:
               if (k > 7) {
                  k -= 8;
                  n++;
                  p--;
               }

               this.s.write = q;
               r = this.s.inflate_flush(r);
               q = this.s.write;
               if (q < this.s.read) {
                  int var10000 = this.s.read - q - 1;
               } else {
                  int var28 = this.s.end - q;
               }

               if (this.s.read != this.s.write) {
                  this.s.bitb = b;
                  this.s.bitk = k;
                  this.z.avail_in = n;
                  this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
                  this.z.next_in_index = p;
                  this.s.write = q;
                  return this.s.inflate_flush(r);
               }

               this.mode = 8;
            case 8:
               int var16 = 1;
               this.s.bitb = b;
               this.s.bitk = k;
               this.z.avail_in = n;
               this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
               this.z.next_in_index = p;
               this.s.write = q;
               return this.s.inflate_flush(var16);
            case 9:
               int var14 = -3;
               this.s.bitb = b;
               this.s.bitk = k;
               this.z.avail_in = n;
               this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
               this.z.next_in_index = p;
               this.s.write = q;
               return this.s.inflate_flush(var14);
            default:
               int var13 = -2;
               this.s.bitb = b;
               this.s.bitk = k;
               this.z.avail_in = n;
               this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
               this.z.next_in_index = p;
               this.s.write = q;
               return this.s.inflate_flush(var13);
         }
      }
   }

   void free(ZStream z) {
   }

   int inflate_fast(int bl, int bd, int[] tl, int tl_index, int[] td, int td_index, InfBlocks s, ZStream z) {
      int p = z.next_in_index;
      int n = z.avail_in;
      int b = s.bitb;
      int k = s.bitk;
      int q = s.write;
      int m = q < s.read ? s.read - q - 1 : s.end - q;
      int ml = inflate_mask[bl];
      int md = inflate_mask[bd];

      label141:
      do {
         while (k < 20) {
            n--;
            b |= (z.next_in[p++] & 255) << k;
            k += 8;
         }

         int t = b & ml;
         int[] tp = tl;
         int tp_index = tl_index;
         int tp_index_t_3 = (tp_index + t) * 3;
         int e;
         if ((e = tp[tp_index_t_3]) == 0) {
            b >>= tp[tp_index_t_3 + 1];
            k -= tp[tp_index_t_3 + 1];
            s.window[q++] = (byte)tp[tp_index_t_3 + 2];
            m--;
         } else {
            do {
               b >>= tp[tp_index_t_3 + 1];
               k -= tp[tp_index_t_3 + 1];
               if ((e & 16) != 0) {
                  e &= 15;
                  int c = tp[tp_index_t_3 + 2] + (b & inflate_mask[e]);
                  b >>= e;

                  for (k -= e; k < 15; k += 8) {
                     n--;
                     b |= (z.next_in[p++] & 255) << k;
                  }

                  t = b & md;
                  tp = td;
                  tp_index = td_index;
                  tp_index_t_3 = (tp_index + t) * 3;
                  e = tp[tp_index_t_3];

                  while (true) {
                     b >>= tp[tp_index_t_3 + 1];
                     k -= tp[tp_index_t_3 + 1];
                     if ((e & 16) != 0) {
                        for (e &= 15; k < e; k += 8) {
                           n--;
                           b |= (z.next_in[p++] & 255) << k;
                        }

                        int d = tp[tp_index_t_3 + 2] + (b & inflate_mask[e]);
                        b >>= e;
                        k -= e;
                        m -= c;
                        int r;
                        if (q >= d) {
                           r = q - d;
                           if (q - r > 0 && 2 > q - r) {
                              s.window[q++] = s.window[r++];
                              s.window[q++] = s.window[r++];
                              c -= 2;
                           } else {
                              System.arraycopy(s.window, r, s.window, q, 2);
                              q += 2;
                              r += 2;
                              c -= 2;
                           }
                        } else {
                           r = q - d;

                           do {
                              r += s.end;
                           } while (r < 0);

                           e = s.end - r;
                           if (c > e) {
                              c -= e;
                              if (q - r > 0 && e > q - r) {
                                 do {
                                    s.window[q++] = s.window[r++];
                                 } while (--e != 0);
                              } else {
                                 System.arraycopy(s.window, r, s.window, q, e);
                                 q += e;
                                 r += e;
                                 int var34 = false;
                              }

                              r = 0;
                           }
                        }

                        if (q - r > 0 && c > q - r) {
                           while (true) {
                              s.window[q++] = s.window[r++];
                              if (--c == 0) {
                                 continue label141;
                              }
                           }
                        } else {
                           System.arraycopy(s.window, r, s.window, q, c);
                           q += c;
                           r += c;
                           int var56 = false;
                           continue label141;
                        }
                     }

                     if ((e & 64) != 0) {
                        z.msg = "invalid distance code";
                        c = z.avail_in - n;
                        c = k >> 3 < c ? k >> 3 : c;
                        n += c;
                        p -= c;
                        k -= c << 3;
                        s.bitb = b;
                        s.bitk = k;
                        z.avail_in = n;
                        z.total_in = z.total_in + (p - z.next_in_index);
                        z.next_in_index = p;
                        s.write = q;
                        return -3;
                     }

                     int var27 = t + tp[tp_index_t_3 + 2];
                     t = var27 + (b & inflate_mask[e]);
                     tp_index_t_3 = (tp_index + t) * 3;
                     e = tp[tp_index_t_3];
                  }
               }

               if ((e & 64) != 0) {
                  if ((e & 32) != 0) {
                     int c = z.avail_in - n;
                     c = k >> 3 < c ? k >> 3 : c;
                     n += c;
                     p -= c;
                     k -= c << 3;
                     s.bitb = b;
                     s.bitk = k;
                     z.avail_in = n;
                     z.total_in = z.total_in + (p - z.next_in_index);
                     z.next_in_index = p;
                     s.write = q;
                     return 1;
                  }

                  z.msg = "invalid literal/length code";
                  int c = z.avail_in - n;
                  c = k >> 3 < c ? k >> 3 : c;
                  n += c;
                  p -= c;
                  k -= c << 3;
                  s.bitb = b;
                  s.bitk = k;
                  z.avail_in = n;
                  z.total_in = z.total_in + (p - z.next_in_index);
                  z.next_in_index = p;
                  s.write = q;
                  return -3;
               }

               int var25 = t + tp[tp_index_t_3 + 2];
               t = var25 + (b & inflate_mask[e]);
               tp_index_t_3 = (tp_index + t) * 3;
            } while ((e = tp[tp_index_t_3]) != 0);

            b >>= tp[tp_index_t_3 + 1];
            k -= tp[tp_index_t_3 + 1];
            s.window[q++] = (byte)tp[tp_index_t_3 + 2];
            m--;
         }
      } while (m >= 258 && n >= 10);

      int c = z.avail_in - n;
      c = k >> 3 < c ? k >> 3 : c;
      n += c;
      p -= c;
      k -= c << 3;
      s.bitb = b;
      s.bitk = k;
      z.avail_in = n;
      z.total_in = z.total_in + (p - z.next_in_index);
      z.next_in_index = p;
      s.write = q;
      return 0;
   }
}
