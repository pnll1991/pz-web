package com.jcraft.jzlib;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

@Deprecated
public class ZInputStream extends FilterInputStream {
   protected int flush = 0;
   protected boolean compress;
   protected InputStream in = null;
   protected Deflater deflater;
   protected InflaterInputStream iis;
   private byte[] buf1 = new byte[1];
   private byte[] buf = new byte[512];

   public ZInputStream(InputStream in) throws IOException {
      this(in, false);
   }

   public ZInputStream(InputStream in, boolean nowrap) throws IOException {
      super(in);
      this.iis = new InflaterInputStream(in, nowrap);
      this.compress = false;
   }

   public ZInputStream(InputStream in, int level) throws IOException {
      super(in);
      this.in = in;
      this.deflater = new Deflater();
      this.deflater.init(level);
      this.compress = true;
   }

   @Override
   public int read() throws IOException {
      return this.read(this.buf1, 0, 1) == -1 ? -1 : this.buf1[0] & 0xFF;
   }

   @Override
   public int read(byte[] b, int off, int len) throws IOException {
      if (!this.compress) {
         return this.iis.read(b, off, len);
      }

      this.deflater.setOutput(b, off, len);

      int err;
      do {
         int datalen = this.in.read(this.buf, 0, this.buf.length);
         if (datalen == -1) {
            return -1;
         }

         this.deflater.setInput(this.buf, 0, datalen, true);
         err = this.deflater.deflate(this.flush);
         if (this.deflater.next_out_index > 0) {
            return this.deflater.next_out_index;
         }

         if (err == 1) {
            return 0;
         }
      } while (err != -2 && err != -3);

      throw new ZStreamException("deflating: " + this.deflater.msg);
   }

   @Override
   public long skip(long n) throws IOException {
      int len = 512;
      if (n < len) {
         len = (int)n;
      }

      byte[] tmp = new byte[len];
      return this.read(tmp);
   }

   public int getFlushMode() {
      return this.flush;
   }

   public void setFlushMode(int flush) {
      this.flush = flush;
   }

   public long getTotalIn() {
      return this.compress ? this.deflater.total_in : this.iis.getTotalIn();
   }

   public long getTotalOut() {
      return this.compress ? this.deflater.total_out : this.iis.getTotalOut();
   }

   @Override
   public void close() throws IOException {
      if (this.compress) {
         this.deflater.end();
      } else {
         this.iis.close();
      }
   }
}
