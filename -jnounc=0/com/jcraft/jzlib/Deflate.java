package com.jcraft.jzlib;

public final class Deflate implements Cloneable {
   private static final int MAX_MEM_LEVEL = 9;
   private static final int Z_DEFAULT_COMPRESSION = -1;
   private static final int MAX_WBITS = 15;
   private static final int DEF_MEM_LEVEL = 8;
   private static final int STORED = 0;
   private static final int FAST = 1;
   private static final int SLOW = 2;
   private static final Deflate.Config[] config_table = new Deflate.Config[10];
   private static final String[] z_errmsg = new String[]{
      "need dictionary", "stream end", "", "file error", "stream error", "data error", "insufficient memory", "buffer error", "incompatible version", ""
   };
   private static final int NeedMore = 0;
   private static final int BlockDone = 1;
   private static final int FinishStarted = 2;
   private static final int FinishDone = 3;
   private static final int PRESET_DICT = 32;
   private static final int Z_FILTERED = 1;
   private static final int Z_HUFFMAN_ONLY = 2;
   private static final int Z_DEFAULT_STRATEGY = 0;
   private static final int Z_NO_FLUSH = 0;
   private static final int Z_PARTIAL_FLUSH = 1;
   private static final int Z_SYNC_FLUSH = 2;
   private static final int Z_FULL_FLUSH = 3;
   private static final int Z_FINISH = 4;
   private static final int Z_OK = 0;
   private static final int Z_STREAM_END = 1;
   private static final int Z_NEED_DICT = 2;
   private static final int Z_ERRNO = -1;
   private static final int Z_STREAM_ERROR = -2;
   private static final int Z_DATA_ERROR = -3;
   private static final int Z_MEM_ERROR = -4;
   private static final int Z_BUF_ERROR = -5;
   private static final int Z_VERSION_ERROR = -6;
   private static final int INIT_STATE = 42;
   private static final int BUSY_STATE = 113;
   private static final int FINISH_STATE = 666;
   private static final int Z_DEFLATED = 8;
   private static final int STORED_BLOCK = 0;
   private static final int STATIC_TREES = 1;
   private static final int DYN_TREES = 2;
   private static final int Z_BINARY = 0;
   private static final int Z_ASCII = 1;
   private static final int Z_UNKNOWN = 2;
   private static final int Buf_size = 16;
   private static final int REP_3_6 = 16;
   private static final int REPZ_3_10 = 17;
   private static final int REPZ_11_138 = 18;
   private static final int MIN_MATCH = 3;
   private static final int MAX_MATCH = 258;
   private static final int MIN_LOOKAHEAD = 262;
   private static final int MAX_BITS = 15;
   private static final int D_CODES = 30;
   private static final int BL_CODES = 19;
   private static final int LENGTH_CODES = 29;
   private static final int LITERALS = 256;
   private static final int L_CODES = 286;
   private static final int HEAP_SIZE = 573;
   private static final int END_BLOCK = 256;
   ZStream strm;
   int status;
   byte[] pending_buf;
   int pending_buf_size;
   int pending_out;
   int pending;
   int wrap = 1;
   byte data_type;
   byte method;
   int last_flush;
   int w_size;
   int w_bits;
   int w_mask;
   byte[] window;
   int window_size;
   short[] prev;
   short[] head;
   int ins_h;
   int hash_size;
   int hash_bits;
   int hash_mask;
   int hash_shift;
   int block_start;
   int match_length;
   int prev_match;
   int match_available;
   int strstart;
   int match_start;
   int lookahead;
   int prev_length;
   int max_chain_length;
   int max_lazy_match;
   int level;
   int strategy;
   int good_match;
   int nice_match;
   short[] dyn_ltree;
   short[] dyn_dtree;
   short[] bl_tree;
   Tree l_desc = new Tree();
   Tree d_desc = new Tree();
   Tree bl_desc = new Tree();
   short[] bl_count = new short[16];
   short[] next_code = new short[16];
   int[] heap = new int[573];
   int heap_len;
   int heap_max;
   byte[] depth = new byte[573];
   byte[] l_buf;
   int lit_bufsize;
   int last_lit;
   int d_buf;
   int opt_len;
   int static_len;
   int matches;
   int last_eob_len;
   short bi_buf;
   int bi_valid;
   GZIPHeader gheader = null;

   Deflate(ZStream strm) {
      this.strm = strm;
      this.dyn_ltree = new short[1146];
      this.dyn_dtree = new short[122];
      this.bl_tree = new short[78];
   }

   void lm_init() {
      this.window_size = 2 * this.w_size;
      this.head[this.hash_size - 1] = 0;

      for (int i = 0; i < this.hash_size - 1; i++) {
         this.head[i] = 0;
      }

      this.max_lazy_match = config_table[this.level].max_lazy;
      this.good_match = config_table[this.level].good_length;
      this.nice_match = config_table[this.level].nice_length;
      this.max_chain_length = config_table[this.level].max_chain;
      this.strstart = 0;
      this.block_start = 0;
      this.lookahead = 0;
      this.match_length = this.prev_length = 2;
      this.match_available = 0;
      this.ins_h = 0;
   }

   void tr_init() {
      this.l_desc.dyn_tree = this.dyn_ltree;
      this.l_desc.stat_desc = StaticTree.static_l_desc;
      this.d_desc.dyn_tree = this.dyn_dtree;
      this.d_desc.stat_desc = StaticTree.static_d_desc;
      this.bl_desc.dyn_tree = this.bl_tree;
      this.bl_desc.stat_desc = StaticTree.static_bl_desc;
      this.bi_buf = 0;
      this.bi_valid = 0;
      this.last_eob_len = 8;
      this.init_block();
   }

   void init_block() {
      for (int i = 0; i < 286; i++) {
         this.dyn_ltree[i * 2] = 0;
      }

      for (int i = 0; i < 30; i++) {
         this.dyn_dtree[i * 2] = 0;
      }

      for (int i = 0; i < 19; i++) {
         this.bl_tree[i * 2] = 0;
      }

      this.dyn_ltree[512] = 1;
      this.opt_len = this.static_len = 0;
      this.last_lit = this.matches = 0;
   }

   void pqdownheap(short[] tree, int k) {
      int v = this.heap[k];

      for (int j = k << 1; j <= this.heap_len; j <<= 1) {
         if (j < this.heap_len && smaller(tree, this.heap[j + 1], this.heap[j], this.depth)) {
            j++;
         }

         if (smaller(tree, v, this.heap[j], this.depth)) {
            break;
         }

         this.heap[k] = this.heap[j];
         k = j;
      }

      this.heap[k] = v;
   }

   static boolean smaller(short[] tree, int n, int m, byte[] depth) {
      short tn2 = tree[n * 2];
      short tm2 = tree[m * 2];
      return tn2 < tm2 || tn2 == tm2 && depth[n] <= depth[m];
   }

   void scan_tree(short[] tree, int max_code) {
      int prevlen = -1;
      int nextlen = tree[1];
      int count = 0;
      int max_count = 7;
      int min_count = 4;
      if (nextlen == 0) {
         max_count = 138;
         min_count = 3;
      }

      tree[(max_code + 1) * 2 + 1] = -1;

      for (int n = 0; n <= max_code; n++) {
         int curlen = nextlen;
         nextlen = tree[(n + 1) * 2 + 1];
         if (++count >= max_count || curlen != nextlen) {
            if (count < min_count) {
               this.bl_tree[curlen * 2] = (short)(this.bl_tree[curlen * 2] + count);
            } else if (curlen != 0) {
               if (curlen != prevlen) {
                  this.bl_tree[curlen * 2]++;
               }

               this.bl_tree[32]++;
            } else if (count <= 10) {
               this.bl_tree[34]++;
            } else {
               this.bl_tree[36]++;
            }

            count = 0;
            prevlen = curlen;
            if (nextlen == 0) {
               max_count = 138;
               min_count = 3;
            } else if (curlen == nextlen) {
               max_count = 6;
               min_count = 3;
            } else {
               max_count = 7;
               min_count = 4;
            }
         }
      }
   }

   int build_bl_tree() {
      this.scan_tree(this.dyn_ltree, this.l_desc.max_code);
      this.scan_tree(this.dyn_dtree, this.d_desc.max_code);
      this.bl_desc.build_tree(this);
      int max_blindex = 18;

      while (max_blindex >= 3 && this.bl_tree[Tree.bl_order[max_blindex] * 2 + 1] == 0) {
         max_blindex--;
      }

      this.opt_len += 3 * (max_blindex + 1) + 5 + 5 + 4;
      return max_blindex;
   }

   void send_all_trees(int lcodes, int dcodes, int blcodes) {
      this.send_bits(lcodes - 257, 5);
      this.send_bits(dcodes - 1, 5);
      this.send_bits(blcodes - 4, 4);

      for (int rank = 0; rank < blcodes; rank++) {
         this.send_bits(this.bl_tree[Tree.bl_order[rank] * 2 + 1], 3);
      }

      this.send_tree(this.dyn_ltree, lcodes - 1);
      this.send_tree(this.dyn_dtree, dcodes - 1);
   }

   void send_tree(short[] tree, int max_code) {
      int prevlen = -1;
      int nextlen = tree[1];
      int count = 0;
      int max_count = 7;
      int min_count = 4;
      if (nextlen == 0) {
         max_count = 138;
         min_count = 3;
      }

      for (int n = 0; n <= max_code; n++) {
         int curlen = nextlen;
         nextlen = tree[(n + 1) * 2 + 1];
         if (++count >= max_count || curlen != nextlen) {
            if (count < min_count) {
               do {
                  this.send_code(curlen, this.bl_tree);
               } while (--count != 0);
            } else if (curlen != 0) {
               if (curlen != prevlen) {
                  this.send_code(curlen, this.bl_tree);
                  count--;
               }

               this.send_code(16, this.bl_tree);
               this.send_bits(count - 3, 2);
            } else if (count <= 10) {
               this.send_code(17, this.bl_tree);
               this.send_bits(count - 3, 3);
            } else {
               this.send_code(18, this.bl_tree);
               this.send_bits(count - 11, 7);
            }

            count = 0;
            prevlen = curlen;
            if (nextlen == 0) {
               max_count = 138;
               min_count = 3;
            } else if (curlen == nextlen) {
               max_count = 6;
               min_count = 3;
            } else {
               max_count = 7;
               min_count = 4;
            }
         }
      }
   }

   final void put_byte(byte[] p, int start, int len) {
      System.arraycopy(p, start, this.pending_buf, this.pending, len);
      this.pending += len;
   }

   final void put_byte(byte c) {
      this.pending_buf[this.pending++] = c;
   }

   final void put_short(int w) {
      this.put_byte((byte)w);
      this.put_byte((byte)(w >>> 8));
   }

   final void putShortMSB(int b) {
      this.put_byte((byte)(b >> 8));
      this.put_byte((byte)b);
   }

   final void send_code(int c, short[] tree) {
      int c2 = c * 2;
      this.send_bits(tree[c2] & 65535, tree[c2 + 1] & 65535);
   }

   void send_bits(int value, int length) {
      int len = length;
      if (this.bi_valid > 16 - len) {
         int val = value;
         this.bi_buf = (short)(this.bi_buf | val << this.bi_valid & 65535);
         this.put_short(this.bi_buf);
         this.bi_buf = (short)(val >>> 16 - this.bi_valid);
         this.bi_valid += len - 16;
      } else {
         this.bi_buf = (short)(this.bi_buf | value << this.bi_valid & 65535);
         this.bi_valid += len;
      }
   }

   void _tr_align() {
      this.send_bits(2, 3);
      this.send_code(256, StaticTree.static_ltree);
      this.bi_flush();
      if (1 + this.last_eob_len + 10 - this.bi_valid < 9) {
         this.send_bits(2, 3);
         this.send_code(256, StaticTree.static_ltree);
         this.bi_flush();
      }

      this.last_eob_len = 7;
   }

   boolean _tr_tally(int dist, int lc) {
      this.pending_buf[this.d_buf + this.last_lit * 2] = (byte)(dist >>> 8);
      this.pending_buf[this.d_buf + this.last_lit * 2 + 1] = (byte)dist;
      this.l_buf[this.last_lit] = (byte)lc;
      this.last_lit++;
      if (dist == 0) {
         this.dyn_ltree[lc * 2]++;
      } else {
         this.matches++;
         dist--;
         this.dyn_ltree[(Tree._length_code[lc] + 256 + 1) * 2]++;
         this.dyn_dtree[Tree.d_code(dist) * 2]++;
      }

      if ((this.last_lit & 8191) == 0 && this.level > 2) {
         int out_length = this.last_lit * 8;
         int in_length = this.strstart - this.block_start;

         for (int dcode = 0; dcode < 30; dcode++) {
            out_length = (int)(out_length + this.dyn_dtree[dcode * 2] * (5L + Tree.extra_dbits[dcode]));
         }

         out_length >>>= 3;
         if (this.matches < this.last_lit / 2 && out_length < in_length / 2) {
            return true;
         }
      }

      return this.last_lit == this.lit_bufsize - 1;
   }

   void compress_block(short[] ltree, short[] dtree) {
      int lx = 0;
      if (this.last_lit != 0) {
         do {
            int dist = this.pending_buf[this.d_buf + lx * 2] << 8 & 0xFF00 | this.pending_buf[this.d_buf + lx * 2 + 1] & 255;
            int lc = this.l_buf[lx] & 255;
            lx++;
            if (dist == 0) {
               this.send_code(lc, ltree);
            } else {
               int code = Tree._length_code[lc];
               this.send_code(code + 256 + 1, ltree);
               int extra = Tree.extra_lbits[code];
               if (extra != 0) {
                  lc -= Tree.base_length[code];
                  this.send_bits(lc, extra);
               }

               code = Tree.d_code(--dist);
               this.send_code(code, dtree);
               extra = Tree.extra_dbits[code];
               if (extra != 0) {
                  dist -= Tree.base_dist[code];
                  this.send_bits(dist, extra);
               }
            }
         } while (lx < this.last_lit);
      }

      this.send_code(256, ltree);
      this.last_eob_len = ltree[513];
   }

   void set_data_type() {
      int n = 0;
      int ascii_freq = 0;
      int bin_freq = 0;

      while (n < 7) {
         bin_freq += this.dyn_ltree[n * 2];
         n++;
      }

      while (n < 128) {
         ascii_freq += this.dyn_ltree[n * 2];
         n++;
      }

      while (n < 256) {
         bin_freq += this.dyn_ltree[n * 2];
         n++;
      }

      this.data_type = (byte)(bin_freq > ascii_freq >>> 2 ? 0 : 1);
   }

   void bi_flush() {
      if (this.bi_valid == 16) {
         this.put_short(this.bi_buf);
         this.bi_buf = 0;
         this.bi_valid = 0;
      } else if (this.bi_valid >= 8) {
         this.put_byte((byte)this.bi_buf);
         this.bi_buf = (short)(this.bi_buf >>> 8);
         this.bi_valid -= 8;
      }
   }

   void bi_windup() {
      if (this.bi_valid > 8) {
         this.put_short(this.bi_buf);
      } else if (this.bi_valid > 0) {
         this.put_byte((byte)this.bi_buf);
      }

      this.bi_buf = 0;
      this.bi_valid = 0;
   }

   void copy_block(int buf, int len, boolean header) {
      int index = 0;
      this.bi_windup();
      this.last_eob_len = 8;
      if (header) {
         this.put_short((short)len);
         this.put_short((short)(~len));
      }

      this.put_byte(this.window, buf, len);
   }

   void flush_block_only(boolean eof) {
      this._tr_flush_block(this.block_start >= 0 ? this.block_start : -1, this.strstart - this.block_start, eof);
      this.block_start = this.strstart;
      this.strm.flush_pending();
   }

   int deflate_stored(int flush) {
      int max_block_size = 65535;
      if (max_block_size > this.pending_buf_size - 5) {
         max_block_size = this.pending_buf_size - 5;
      }

      while (true) {
         if (this.lookahead <= 1) {
            this.fill_window();
            if (this.lookahead == 0 && flush == 0) {
               return 0;
            }

            if (this.lookahead == 0) {
               this.flush_block_only(flush == 4);
               if (this.strm.avail_out == 0) {
                  return flush == 4 ? 2 : 0;
               }

               return flush == 4 ? 3 : 1;
            }
         }

         this.strstart = this.strstart + this.lookahead;
         this.lookahead = 0;
         int max_start = this.block_start + max_block_size;
         if (this.strstart == 0 || this.strstart >= max_start) {
            this.lookahead = this.strstart - max_start;
            this.strstart = max_start;
            this.flush_block_only(false);
            if (this.strm.avail_out == 0) {
               return 0;
            }
         }

         if (this.strstart - this.block_start >= this.w_size - 262) {
            this.flush_block_only(false);
            if (this.strm.avail_out == 0) {
               return 0;
            }
         }
      }
   }

   void _tr_stored_block(int buf, int stored_len, boolean eof) {
      this.send_bits(0 + (eof ? 1 : 0), 3);
      this.copy_block(buf, stored_len, true);
   }

   void _tr_flush_block(int buf, int stored_len, boolean eof) {
      int max_blindex = 0;
      int opt_lenb;
      int static_lenb;
      if (this.level > 0) {
         if (this.data_type == 2) {
            this.set_data_type();
         }

         this.l_desc.build_tree(this);
         this.d_desc.build_tree(this);
         max_blindex = this.build_bl_tree();
         opt_lenb = this.opt_len + 3 + 7 >>> 3;
         static_lenb = this.static_len + 3 + 7 >>> 3;
         if (static_lenb <= opt_lenb) {
            opt_lenb = static_lenb;
         }
      } else {
         opt_lenb = static_lenb = stored_len + 5;
      }

      if (stored_len + 4 <= opt_lenb && buf != -1) {
         this._tr_stored_block(buf, stored_len, eof);
      } else if (static_lenb == opt_lenb) {
         this.send_bits(2 + (eof ? 1 : 0), 3);
         this.compress_block(StaticTree.static_ltree, StaticTree.static_dtree);
      } else {
         this.send_bits(4 + (eof ? 1 : 0), 3);
         this.send_all_trees(this.l_desc.max_code + 1, this.d_desc.max_code + 1, max_blindex + 1);
         this.compress_block(this.dyn_ltree, this.dyn_dtree);
      }

      this.init_block();
      if (eof) {
         this.bi_windup();
      }
   }

   void fill_window() {
      do {
         int more = this.window_size - this.lookahead - this.strstart;
         if (more == 0 && this.strstart == 0 && this.lookahead == 0) {
            more = this.w_size;
         } else if (more == -1) {
            more--;
         } else if (this.strstart >= this.w_size + this.w_size - 262) {
            System.arraycopy(this.window, this.w_size, this.window, 0, this.w_size);
            this.match_start = this.match_start - this.w_size;
            this.strstart = this.strstart - this.w_size;
            this.block_start = this.block_start - this.w_size;
            int n = this.hash_size;
            int p = n;

            do {
               int m = this.head[--p] & '\uffff';
               this.head[p] = m >= this.w_size ? (short)(m - this.w_size) : 0;
            } while (--n != 0);

            n = this.w_size;
            p = n;

            do {
               int var7 = this.prev[--p] & '\uffff';
               this.prev[p] = var7 >= this.w_size ? (short)(var7 - this.w_size) : 0;
            } while (--n != 0);

            more += this.w_size;
         }

         if (this.strm.avail_in == 0) {
            return;
         }

         int n = this.strm.read_buf(this.window, this.strstart + this.lookahead, more);
         this.lookahead += n;
         if (this.lookahead >= 3) {
            this.ins_h = this.window[this.strstart] & 255;
            this.ins_h = (this.ins_h << this.hash_shift ^ this.window[this.strstart + 1] & 255) & this.hash_mask;
         }
      } while (this.lookahead < 262 && this.strm.avail_in != 0);
   }

   int deflate_fast(int flush) {
      int hash_head = 0;

      while (true) {
         if (this.lookahead < 262) {
            this.fill_window();
            if (this.lookahead < 262 && flush == 0) {
               return 0;
            }

            if (this.lookahead == 0) {
               this.flush_block_only(flush == 4);
               if (this.strm.avail_out == 0) {
                  if (flush == 4) {
                     return 2;
                  }

                  return 0;
               }

               return flush == 4 ? 3 : 1;
            }
         }

         if (this.lookahead >= 3) {
            this.ins_h = (this.ins_h << this.hash_shift ^ this.window[this.strstart + 2] & 255) & this.hash_mask;
            hash_head = this.head[this.ins_h] & '\uffff';
            this.prev[this.strstart & this.w_mask] = this.head[this.ins_h];
            this.head[this.ins_h] = (short)this.strstart;
         }

         if (hash_head != 0L && (this.strstart - hash_head & 65535) <= this.w_size - 262 && this.strategy != 2) {
            this.match_length = this.longest_match(hash_head);
         }

         boolean bflush;
         if (this.match_length < 3) {
            bflush = this._tr_tally(0, this.window[this.strstart] & 255);
            this.lookahead--;
            this.strstart++;
         } else {
            bflush = this._tr_tally(this.strstart - this.match_start, this.match_length - 3);
            this.lookahead = this.lookahead - this.match_length;
            if (this.match_length <= this.max_lazy_match && this.lookahead >= 3) {
               this.match_length--;

               do {
                  this.strstart++;
                  this.ins_h = (this.ins_h << this.hash_shift ^ this.window[this.strstart + 2] & 255) & this.hash_mask;
                  hash_head = this.head[this.ins_h] & '\uffff';
                  this.prev[this.strstart & this.w_mask] = this.head[this.ins_h];
                  this.head[this.ins_h] = (short)this.strstart;
               } while (--this.match_length != 0);

               this.strstart++;
            } else {
               this.strstart = this.strstart + this.match_length;
               this.match_length = 0;
               this.ins_h = this.window[this.strstart] & 255;
               this.ins_h = (this.ins_h << this.hash_shift ^ this.window[this.strstart + 1] & 255) & this.hash_mask;
            }
         }

         if (bflush) {
            this.flush_block_only(false);
            if (this.strm.avail_out == 0) {
               return 0;
            }
         }
      }
   }

   int deflate_slow(int flush) {
      int hash_head = 0;

      while (true) {
         if (this.lookahead < 262) {
            this.fill_window();
            if (this.lookahead < 262 && flush == 0) {
               return 0;
            }

            if (this.lookahead == 0) {
               if (this.match_available != 0) {
                  boolean bflush = this._tr_tally(0, this.window[this.strstart - 1] & 255);
                  this.match_available = 0;
               }

               this.flush_block_only(flush == 4);
               if (this.strm.avail_out == 0) {
                  if (flush == 4) {
                     return 2;
                  }

                  return 0;
               }

               return flush == 4 ? 3 : 1;
            }
         }

         if (this.lookahead >= 3) {
            this.ins_h = (this.ins_h << this.hash_shift ^ this.window[this.strstart + 2] & 255) & this.hash_mask;
            hash_head = this.head[this.ins_h] & '\uffff';
            this.prev[this.strstart & this.w_mask] = this.head[this.ins_h];
            this.head[this.ins_h] = (short)this.strstart;
         }

         this.prev_length = this.match_length;
         this.prev_match = this.match_start;
         this.match_length = 2;
         if (hash_head != 0 && this.prev_length < this.max_lazy_match && (this.strstart - hash_head & 65535) <= this.w_size - 262) {
            if (this.strategy != 2) {
               this.match_length = this.longest_match(hash_head);
            }

            if (this.match_length <= 5 && (this.strategy == 1 || this.match_length == 3 && this.strstart - this.match_start > 4096)) {
               this.match_length = 2;
            }
         }

         if (this.prev_length >= 3 && this.match_length <= this.prev_length) {
            int max_insert = this.strstart + this.lookahead - 3;
            boolean bflush = this._tr_tally(this.strstart - 1 - this.prev_match, this.prev_length - 3);
            this.lookahead = this.lookahead - (this.prev_length - 1);
            this.prev_length -= 2;

            do {
               if (++this.strstart <= max_insert) {
                  this.ins_h = (this.ins_h << this.hash_shift ^ this.window[this.strstart + 2] & 255) & this.hash_mask;
                  hash_head = this.head[this.ins_h] & '\uffff';
                  this.prev[this.strstart & this.w_mask] = this.head[this.ins_h];
                  this.head[this.ins_h] = (short)this.strstart;
               }
            } while (--this.prev_length != 0);

            this.match_available = 0;
            this.match_length = 2;
            this.strstart++;
            if (bflush) {
               this.flush_block_only(false);
               if (this.strm.avail_out == 0) {
                  return 0;
               }
            }
         } else if (this.match_available != 0) {
            boolean bflush = this._tr_tally(0, this.window[this.strstart - 1] & 255);
            if (bflush) {
               this.flush_block_only(false);
            }

            this.strstart++;
            this.lookahead--;
            if (this.strm.avail_out == 0) {
               return 0;
            }
         } else {
            this.match_available = 1;
            this.strstart++;
            this.lookahead--;
         }
      }
   }

   int longest_match(int cur_match) {
      int chain_length = this.max_chain_length;
      int scan = this.strstart;
      int best_len = this.prev_length;
      int limit = this.strstart > this.w_size - 262 ? this.strstart - (this.w_size - 262) : 0;
      int nice_match = this.nice_match;
      int wmask = this.w_mask;
      int strend = this.strstart + 258;
      byte scan_end1 = this.window[scan + best_len - 1];
      byte scan_end = this.window[scan + best_len];
      if (this.prev_length >= this.good_match) {
         chain_length >>= 2;
      }

      if (nice_match > this.lookahead) {
         nice_match = this.lookahead;
      }

      do {
         int match = cur_match;
         if (this.window[match + best_len] == scan_end && this.window[match + best_len - 1] == scan_end1 && this.window[match] == this.window[scan]) {
            if (this.window[++match] == this.window[scan + 1]) {
               scan += 2;
               match++;

               while (
                  this.window[++scan] == this.window[++match]
                     && this.window[++scan] == this.window[++match]
                     && this.window[++scan] == this.window[++match]
                     && this.window[++scan] == this.window[++match]
                     && this.window[++scan] == this.window[++match]
                     && this.window[++scan] == this.window[++match]
                     && this.window[++scan] == this.window[++match]
                     && this.window[++scan] == this.window[++match]
                     && scan < strend
               ) {
               }

               int len = 258 - (strend - scan);
               scan = strend - 258;
               if (len > best_len) {
                  this.match_start = cur_match;
                  best_len = len;
                  if (len >= nice_match) {
                     break;
                  }

                  scan_end1 = this.window[scan + best_len - 1];
                  scan_end = this.window[scan + best_len];
               }
            }
         }
      } while ((cur_match = this.prev[cur_match & wmask] & '\uffff') > limit && --chain_length != 0);

      return best_len <= this.lookahead ? best_len : this.lookahead;
   }

   int deflateInit(int level, int bits, int memlevel) {
      return this.deflateInit(level, 8, bits, memlevel, 0);
   }

   int deflateInit(int level, int bits) {
      return this.deflateInit(level, 8, bits, 8, 0);
   }

   int deflateInit(int level) {
      return this.deflateInit(level, 15);
   }

   private int deflateInit(int level, int method, int windowBits, int memLevel, int strategy) {
      int wrap = 1;
      this.strm.msg = null;
      if (level == -1) {
         level = 6;
      }

      if (windowBits < 0) {
         wrap = 0;
         windowBits = -windowBits;
      } else if (windowBits > 15) {
         wrap = 2;
         windowBits -= 16;
         this.strm.adler = new CRC32();
      }

      if (memLevel >= 1 && memLevel <= 9 && method == 8 && windowBits >= 9 && windowBits <= 15 && level >= 0 && level <= 9 && strategy >= 0 && strategy <= 2) {
         this.strm.dstate = this;
         this.wrap = wrap;
         this.w_bits = windowBits;
         this.w_size = 1 << this.w_bits;
         this.w_mask = this.w_size - 1;
         this.hash_bits = memLevel + 7;
         this.hash_size = 1 << this.hash_bits;
         this.hash_mask = this.hash_size - 1;
         this.hash_shift = (this.hash_bits + 3 - 1) / 3;
         this.window = new byte[this.w_size * 2];
         this.prev = new short[this.w_size];
         this.head = new short[this.hash_size];
         this.lit_bufsize = 1 << memLevel + 6;
         this.pending_buf = new byte[this.lit_bufsize * 3];
         this.pending_buf_size = this.lit_bufsize * 3;
         this.d_buf = this.lit_bufsize;
         this.l_buf = new byte[this.lit_bufsize];
         this.level = level;
         this.strategy = strategy;
         this.method = (byte)method;
         return this.deflateReset();
      } else {
         return -2;
      }
   }

   int deflateReset() {
      this.strm.total_in = this.strm.total_out = 0L;
      this.strm.msg = null;
      this.strm.data_type = 2;
      this.pending = 0;
      this.pending_out = 0;
      if (this.wrap < 0) {
         this.wrap = -this.wrap;
      }

      this.status = this.wrap == 0 ? 113 : 42;
      this.strm.adler.reset();
      this.last_flush = 0;
      this.tr_init();
      this.lm_init();
      return 0;
   }

   int deflateEnd() {
      if (this.status != 42 && this.status != 113 && this.status != 666) {
         return -2;
      }

      this.pending_buf = null;
      this.l_buf = null;
      this.head = null;
      this.prev = null;
      this.window = null;
      return this.status == 113 ? -3 : 0;
   }

   int deflateParams(int _level, int _strategy) {
      int err = 0;
      if (_level == -1) {
         _level = 6;
      }

      if (_level >= 0 && _level <= 9 && _strategy >= 0 && _strategy <= 2) {
         if (config_table[this.level].func != config_table[_level].func && this.strm.total_in != 0L) {
            err = this.strm.deflate(1);
         }

         if (this.level != _level) {
            this.level = _level;
            this.max_lazy_match = config_table[this.level].max_lazy;
            this.good_match = config_table[this.level].good_length;
            this.nice_match = config_table[this.level].nice_length;
            this.max_chain_length = config_table[this.level].max_chain;
         }

         this.strategy = _strategy;
         return err;
      } else {
         return -2;
      }
   }

   int deflateSetDictionary(byte[] dictionary, int dictLength) {
      int length = dictLength;
      int index = 0;
      if (dictionary != null && this.status == 42) {
         this.strm.adler.update(dictionary, 0, dictLength);
         if (length < 3) {
            return 0;
         }

         if (length > this.w_size - 262) {
            length = this.w_size - 262;
            index = dictLength - length;
         }

         System.arraycopy(dictionary, index, this.window, 0, length);
         this.strstart = length;
         this.block_start = length;
         this.ins_h = this.window[0] & 255;
         this.ins_h = (this.ins_h << this.hash_shift ^ this.window[1] & 255) & this.hash_mask;

         for (int n = 0; n <= length - 3; n++) {
            this.ins_h = (this.ins_h << this.hash_shift ^ this.window[n + 2] & 255) & this.hash_mask;
            this.prev[n & this.w_mask] = this.head[this.ins_h];
            this.head[this.ins_h] = (short)n;
         }

         return 0;
      } else {
         return -2;
      }
   }

   int deflate(int flush) {
      if (flush <= 4 && flush >= 0) {
         if (this.strm.next_out != null && (this.strm.next_in != null || this.strm.avail_in == 0) && (this.status != 666 || flush == 4)) {
            if (this.strm.avail_out == 0) {
               this.strm.msg = z_errmsg[7];
               return -5;
            }

            int old_flush = this.last_flush;
            this.last_flush = flush;
            if (this.status == 42) {
               if (this.wrap == 2) {
                  this.getGZIPHeader().put(this);
                  this.status = 113;
                  this.strm.adler.reset();
               } else {
                  int header = 8 + (this.w_bits - 8 << 4) << 8;
                  int level_flags = (this.level - 1 & 0xFF) >> 1;
                  if (level_flags > 3) {
                     level_flags = 3;
                  }

                  header |= level_flags << 6;
                  if (this.strstart != 0) {
                     header |= 32;
                  }

                  header += 31 - header % 31;
                  this.status = 113;
                  this.putShortMSB(header);
                  if (this.strstart != 0) {
                     long adler = this.strm.adler.getValue();
                     this.putShortMSB((int)(adler >>> 16));
                     this.putShortMSB((int)(adler & 65535L));
                  }

                  this.strm.adler.reset();
               }
            }

            if (this.pending != 0) {
               this.strm.flush_pending();
               if (this.strm.avail_out == 0) {
                  this.last_flush = -1;
                  return 0;
               }
            } else if (this.strm.avail_in == 0 && flush <= old_flush && flush != 4) {
               this.strm.msg = z_errmsg[7];
               return -5;
            }

            if (this.status == 666 && this.strm.avail_in != 0) {
               this.strm.msg = z_errmsg[7];
               return -5;
            }

            if (this.strm.avail_in != 0 || this.lookahead != 0 || flush != 0 && this.status != 666) {
               int bstate = -1;
               switch (config_table[this.level].func) {
                  case 0:
                     bstate = this.deflate_stored(flush);
                     break;
                  case 1:
                     bstate = this.deflate_fast(flush);
                     break;
                  case 2:
                     bstate = this.deflate_slow(flush);
               }

               if (bstate == 2 || bstate == 3) {
                  this.status = 666;
               }

               if (bstate == 0 || bstate == 2) {
                  if (this.strm.avail_out == 0) {
                     this.last_flush = -1;
                  }

                  return 0;
               }

               if (bstate == 1) {
                  if (flush == 1) {
                     this._tr_align();
                  } else {
                     this._tr_stored_block(0, 0, false);
                     if (flush == 3) {
                        for (int i = 0; i < this.hash_size; i++) {
                           this.head[i] = 0;
                        }
                     }
                  }

                  this.strm.flush_pending();
                  if (this.strm.avail_out == 0) {
                     this.last_flush = -1;
                     return 0;
                  }
               }
            }

            if (flush != 4) {
               return 0;
            }

            if (this.wrap <= 0) {
               return 1;
            }

            if (this.wrap == 2) {
               long adler = this.strm.adler.getValue();
               this.put_byte((byte)(adler & 255L));
               this.put_byte((byte)(adler >> 8 & 255L));
               this.put_byte((byte)(adler >> 16 & 255L));
               this.put_byte((byte)(adler >> 24 & 255L));
               this.put_byte((byte)(this.strm.total_in & 255L));
               this.put_byte((byte)(this.strm.total_in >> 8 & 255L));
               this.put_byte((byte)(this.strm.total_in >> 16 & 255L));
               this.put_byte((byte)(this.strm.total_in >> 24 & 255L));
               this.getGZIPHeader().setCRC(adler);
            } else {
               long adler = this.strm.adler.getValue();
               this.putShortMSB((int)(adler >>> 16));
               this.putShortMSB((int)(adler & 65535L));
            }

            this.strm.flush_pending();
            if (this.wrap > 0) {
               this.wrap = -this.wrap;
            }

            return this.pending != 0 ? 0 : 1;
         } else {
            this.strm.msg = z_errmsg[4];
            return -2;
         }
      } else {
         return -2;
      }
   }

   static int deflateCopy(ZStream dest, ZStream src) {
      if (src.dstate == null) {
         return -2;
      }

      if (src.next_in != null) {
         dest.next_in = new byte[src.next_in.length];
         System.arraycopy(src.next_in, 0, dest.next_in, 0, src.next_in.length);
      }

      dest.next_in_index = src.next_in_index;
      dest.avail_in = src.avail_in;
      dest.total_in = src.total_in;
      if (src.next_out != null) {
         dest.next_out = new byte[src.next_out.length];
         System.arraycopy(src.next_out, 0, dest.next_out, 0, src.next_out.length);
      }

      dest.next_out_index = src.next_out_index;
      dest.avail_out = src.avail_out;
      dest.total_out = src.total_out;
      dest.msg = src.msg;
      dest.data_type = src.data_type;
      dest.adler = src.adler.copy();

      try {
         dest.dstate = (Deflate)src.dstate.clone();
         dest.dstate.strm = dest;
      } catch (CloneNotSupportedException e) {
      }

      return 0;
   }

   @Override
   public Object clone() throws CloneNotSupportedException {
      Deflate dest = (Deflate)super.clone();
      dest.pending_buf = this.dup(dest.pending_buf);
      dest.d_buf = dest.d_buf;
      dest.l_buf = this.dup(dest.l_buf);
      dest.window = this.dup(dest.window);
      dest.prev = this.dup(dest.prev);
      dest.head = this.dup(dest.head);
      dest.dyn_ltree = this.dup(dest.dyn_ltree);
      dest.dyn_dtree = this.dup(dest.dyn_dtree);
      dest.bl_tree = this.dup(dest.bl_tree);
      dest.bl_count = this.dup(dest.bl_count);
      dest.next_code = this.dup(dest.next_code);
      dest.heap = this.dup(dest.heap);
      dest.depth = this.dup(dest.depth);
      dest.l_desc.dyn_tree = dest.dyn_ltree;
      dest.d_desc.dyn_tree = dest.dyn_dtree;
      dest.bl_desc.dyn_tree = dest.bl_tree;
      if (dest.gheader != null) {
         dest.gheader = (GZIPHeader)dest.gheader.clone();
      }

      return dest;
   }

   private byte[] dup(byte[] buf) {
      byte[] foo = new byte[buf.length];
      System.arraycopy(buf, 0, foo, 0, foo.length);
      return foo;
   }

   private short[] dup(short[] buf) {
      short[] foo = new short[buf.length];
      System.arraycopy(buf, 0, foo, 0, foo.length);
      return foo;
   }

   private int[] dup(int[] buf) {
      int[] foo = new int[buf.length];
      System.arraycopy(buf, 0, foo, 0, foo.length);
      return foo;
   }

   synchronized GZIPHeader getGZIPHeader() {
      if (this.gheader == null) {
         this.gheader = new GZIPHeader();
      }

      return this.gheader;
   }

   static {
      config_table[0] = new Deflate.Config(0, 0, 0, 0, 0);
      config_table[1] = new Deflate.Config(4, 4, 8, 4, 1);
      config_table[2] = new Deflate.Config(4, 5, 16, 8, 1);
      config_table[3] = new Deflate.Config(4, 6, 32, 32, 1);
      config_table[4] = new Deflate.Config(4, 4, 16, 16, 2);
      config_table[5] = new Deflate.Config(8, 16, 32, 32, 2);
      config_table[6] = new Deflate.Config(8, 16, 128, 128, 2);
      config_table[7] = new Deflate.Config(8, 32, 128, 256, 2);
      config_table[8] = new Deflate.Config(32, 128, 258, 1024, 2);
      config_table[9] = new Deflate.Config(32, 258, 258, 4096, 2);
   }

   static class Config {
      int good_length;
      int max_lazy;
      int nice_length;
      int max_chain;
      int func;

      Config(int good_length, int max_lazy, int nice_length, int max_chain, int func) {
         this.good_length = good_length;
         this.max_lazy = max_lazy;
         this.nice_length = nice_length;
         this.max_chain = max_chain;
         this.func = func;
      }
   }
}
