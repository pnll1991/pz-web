package com.jcraft.jzlib;

public final class Inflater extends ZStream {
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
   private boolean finished = false;

   public Inflater() {
      this.init();
   }

   public Inflater(JZlib.WrapperType wrapperType) throws GZIPException {
      this(15, wrapperType);
   }

   public Inflater(int w, JZlib.WrapperType wrapperType) throws GZIPException {
      int ret = this.init(w, wrapperType);
      if (ret != 0) {
         throw new GZIPException(ret + ": " + this.msg);
      }
   }

   public Inflater(int w) throws GZIPException {
      this(w, false);
   }

   public Inflater(boolean nowrap) throws GZIPException {
      this(15, nowrap);
   }

   public Inflater(int w, boolean nowrap) throws GZIPException {
      int ret = this.init(w, nowrap);
      if (ret != 0) {
         throw new GZIPException(ret + ": " + this.msg);
      }
   }

   public int init() {
      return this.init(15);
   }

   public int init(JZlib.WrapperType wrapperType) {
      return this.init(15, wrapperType);
   }

   public int init(int w, JZlib.WrapperType wrapperType) {
      boolean nowrap = false;
      if (wrapperType == JZlib.W_NONE) {
         nowrap = true;
      } else if (wrapperType == JZlib.W_GZIP) {
         w += 16;
      } else if (wrapperType == JZlib.W_ANY) {
         w |= 1073741824;
      } else if (wrapperType == JZlib.W_ZLIB) {
      }

      return this.init(w, nowrap);
   }

   public int init(boolean nowrap) {
      return this.init(15, nowrap);
   }

   public int init(int w) {
      return this.init(w, false);
   }

   public int init(int w, boolean nowrap) {
      this.finished = false;
      this.istate = new Inflate(this);
      return this.istate.inflateInit(nowrap ? -w : w);
   }

   @Override
   public int inflate(int f) {
      if (this.istate == null) {
         return -2;
      }

      int ret = this.istate.inflate(f);
      if (ret == 1) {
         this.finished = true;
      }

      return ret;
   }

   @Override
   public int end() {
      this.finished = true;
      return this.istate == null ? -2 : this.istate.inflateEnd();
   }

   public int sync() {
      return this.istate == null ? -2 : this.istate.inflateSync();
   }

   public int syncPoint() {
      return this.istate == null ? -2 : this.istate.inflateSyncPoint();
   }

   public int setDictionary(byte[] dictionary, int dictLength) {
      return this.istate == null ? -2 : this.istate.inflateSetDictionary(dictionary, dictLength);
   }

   @Override
   public boolean finished() {
      return this.istate.mode == 12;
   }
}
