package com.jcraft.jzlib;

@Deprecated
public class ZStream {
   private static final int MAX_WBITS = 15;
   private static final int DEF_WBITS = 15;
   private static final int Z_NO_FLUSH = 0;
   private static final int Z_PARTIAL_FLUSH = 1;
   private static final int Z_SYNC_FLUSH = 2;
   private static final int Z_FULL_FLUSH = 3;
   private static final int Z_FINISH = 4;
   private static final int MAX_MEM_LEVEL = 9;
   private static final int Z_OK = 0;
   private static final int Z_STREAM_END = 1;
   private static final int Z_NEED_DICT = 2;
   private static final int Z_ERRNO = -1;
   private static final int Z_STREAM_ERROR = -2;
   private static final int Z_DATA_ERROR = -3;
   private static final int Z_MEM_ERROR = -4;
   private static final int Z_BUF_ERROR = -5;
   private static final int Z_VERSION_ERROR = -6;
   public byte[] next_in;
   public int next_in_index;
   public int avail_in;
   public long total_in;
   public byte[] next_out;
   public int next_out_index;
   public int avail_out;
   public long total_out;
   public String msg;
   Deflate dstate;
   Inflate istate;
   int data_type;
   Checksum adler;

   public ZStream() {
      this(new Adler32());
   }

   public ZStream(Checksum adler) {
      this.adler = adler;
   }

   public int inflateInit() {
      return this.inflateInit(15);
   }

   public int inflateInit(boolean nowrap) {
      return this.inflateInit(15, nowrap);
   }

   public int inflateInit(int w) {
      return this.inflateInit(w, false);
   }

   public int inflateInit(JZlib.WrapperType wrapperType) {
      return this.inflateInit(15, wrapperType);
   }

   public int inflateInit(int w, JZlib.WrapperType wrapperType) {
      boolean nowrap = false;
      if (wrapperType == JZlib.W_NONE) {
         nowrap = true;
      } else if (wrapperType == JZlib.W_GZIP) {
         w += 16;
      } else if (wrapperType == JZlib.W_ANY) {
         w |= 1073741824;
      } else if (wrapperType == JZlib.W_ZLIB) {
      }

      return this.inflateInit(w, nowrap);
   }

   public int inflateInit(int w, boolean nowrap) {
      this.istate = new Inflate(this);
      return this.istate.inflateInit(nowrap ? -w : w);
   }

   public int inflate(int f) {
      return this.istate == null ? -2 : this.istate.inflate(f);
   }

   public int inflateEnd() {
      return this.istate == null ? -2 : this.istate.inflateEnd();
   }

   public int inflateSync() {
      return this.istate == null ? -2 : this.istate.inflateSync();
   }

   public int inflateSyncPoint() {
      return this.istate == null ? -2 : this.istate.inflateSyncPoint();
   }

   public int inflateSetDictionary(byte[] dictionary, int dictLength) {
      return this.istate == null ? -2 : this.istate.inflateSetDictionary(dictionary, dictLength);
   }

   public boolean inflateFinished() {
      return this.istate.mode == 12;
   }

   public int deflateInit(int level) {
      return this.deflateInit(level, 15);
   }

   public int deflateInit(int level, boolean nowrap) {
      return this.deflateInit(level, 15, nowrap);
   }

   public int deflateInit(int level, int bits) {
      return this.deflateInit(level, bits, false);
   }

   public int deflateInit(int level, int bits, int memlevel, JZlib.WrapperType wrapperType) {
      if (bits >= 9 && bits <= 15) {
         if (wrapperType == JZlib.W_NONE) {
            bits *= -1;
         } else if (wrapperType == JZlib.W_GZIP) {
            bits += 16;
         } else {
            if (wrapperType == JZlib.W_ANY) {
               return -2;
            }

            if (wrapperType == JZlib.W_ZLIB) {
            }
         }

         return this.deflateInit(level, bits, memlevel);
      } else {
         return -2;
      }
   }

   public int deflateInit(int level, int bits, int memlevel) {
      this.dstate = new Deflate(this);
      return this.dstate.deflateInit(level, bits, memlevel);
   }

   public int deflateInit(int level, int bits, boolean nowrap) {
      this.dstate = new Deflate(this);
      return this.dstate.deflateInit(level, nowrap ? -bits : bits);
   }

   public int deflate(int flush) {
      return this.dstate == null ? -2 : this.dstate.deflate(flush);
   }

   public int deflateEnd() {
      if (this.dstate == null) {
         return -2;
      }

      int ret = this.dstate.deflateEnd();
      this.dstate = null;
      return ret;
   }

   public int deflateParams(int level, int strategy) {
      return this.dstate == null ? -2 : this.dstate.deflateParams(level, strategy);
   }

   public int deflateSetDictionary(byte[] dictionary, int dictLength) {
      return this.dstate == null ? -2 : this.dstate.deflateSetDictionary(dictionary, dictLength);
   }

   void flush_pending() {
      int len = this.dstate.pending;
      if (len > this.avail_out) {
         len = this.avail_out;
      }

      if (len != 0) {
         if (this.dstate.pending_buf.length > this.dstate.pending_out
            && this.next_out.length > this.next_out_index
            && this.dstate.pending_buf.length >= this.dstate.pending_out + len
            && this.next_out.length < this.next_out_index + len) {
         }

         System.arraycopy(this.dstate.pending_buf, this.dstate.pending_out, this.next_out, this.next_out_index, len);
         this.next_out_index += len;
         this.dstate.pending_out += len;
         this.total_out += len;
         this.avail_out -= len;
         this.dstate.pending -= len;
         if (this.dstate.pending == 0) {
            this.dstate.pending_out = 0;
         }
      }
   }

   int read_buf(byte[] buf, int start, int size) {
      int len = this.avail_in;
      if (len > size) {
         len = size;
      }

      if (len == 0) {
         return 0;
      }

      this.avail_in -= len;
      if (this.dstate.wrap != 0) {
         this.adler.update(this.next_in, this.next_in_index, len);
      }

      System.arraycopy(this.next_in, this.next_in_index, buf, start, len);
      this.next_in_index += len;
      this.total_in += len;
      return len;
   }

   public long getAdler() {
      return this.adler.getValue();
   }

   public void free() {
      this.next_in = null;
      this.next_out = null;
      this.msg = null;
   }

   public void setOutput(byte[] buf) {
      this.setOutput(buf, 0, buf.length);
   }

   public void setOutput(byte[] buf, int off, int len) {
      this.next_out = buf;
      this.next_out_index = off;
      this.avail_out = len;
   }

   public void setInput(byte[] buf) {
      this.setInput(buf, 0, buf.length, false);
   }

   public void setInput(byte[] buf, boolean append) {
      this.setInput(buf, 0, buf.length, append);
   }

   public void setInput(byte[] buf, int off, int len, boolean append) {
      if (len > 0 || !append || this.next_in == null) {
         if (this.avail_in > 0 && append) {
            byte[] tmp = new byte[this.avail_in + len];
            System.arraycopy(this.next_in, this.next_in_index, tmp, 0, this.avail_in);
            System.arraycopy(buf, off, tmp, this.avail_in, len);
            this.next_in = tmp;
            this.next_in_index = 0;
            this.avail_in += len;
         } else {
            this.next_in = buf;
            this.next_in_index = off;
            this.avail_in = len;
         }
      }
   }

   public byte[] getNextIn() {
      return this.next_in;
   }

   public void setNextIn(byte[] next_in) {
      this.next_in = next_in;
   }

   public int getNextInIndex() {
      return this.next_in_index;
   }

   public void setNextInIndex(int next_in_index) {
      this.next_in_index = next_in_index;
   }

   public int getAvailIn() {
      return this.avail_in;
   }

   public void setAvailIn(int avail_in) {
      this.avail_in = avail_in;
   }

   public byte[] getNextOut() {
      return this.next_out;
   }

   public void setNextOut(byte[] next_out) {
      this.next_out = next_out;
   }

   public int getNextOutIndex() {
      return this.next_out_index;
   }

   public void setNextOutIndex(int next_out_index) {
      this.next_out_index = next_out_index;
   }

   public int getAvailOut() {
      return this.avail_out;
   }

   public void setAvailOut(int avail_out) {
      this.avail_out = avail_out;
   }

   public long getTotalOut() {
      return this.total_out;
   }

   public long getTotalIn() {
      return this.total_in;
   }

   public String getMessage() {
      return this.msg;
   }

   public int end() {
      return 0;
   }

   public boolean finished() {
      return false;
   }
}
