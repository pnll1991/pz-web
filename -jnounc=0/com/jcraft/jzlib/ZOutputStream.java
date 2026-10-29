package com.jcraft.jzlib;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Deprecated
public class ZOutputStream extends FilterOutputStream {
   protected int bufsize = 512;
   protected int flush = 0;
   protected byte[] buf = new byte[this.bufsize];
   protected boolean compress;
   protected OutputStream out;
   private boolean end = false;
   private DeflaterOutputStream dos;
   private Inflater inflater;
   private byte[] buf1 = new byte[1];

   public ZOutputStream(OutputStream out) throws IOException {
      super(out);
      this.out = out;
      this.inflater = new Inflater();
      this.inflater.init();
      this.compress = false;
   }

   public ZOutputStream(OutputStream out, int level) throws IOException {
      this(out, level, false);
   }

   public ZOutputStream(OutputStream out, int level, boolean nowrap) throws IOException {
      super(out);
      this.out = out;
      Deflater deflater = new Deflater(level, nowrap);
      this.dos = new DeflaterOutputStream(out, deflater);
      this.compress = true;
   }

   @Override
   public void write(int b) throws IOException {
      this.buf1[0] = (byte)b;
      this.write(this.buf1, 0, 1);
   }

   @Override
   public void write(byte[] b, int off, int len) throws IOException {
      if (len != 0) {
         if (this.compress) {
            this.dos.write(b, off, len);
         } else {
            this.inflater.setInput(b, off, len, true);
            int err = 0;

            while (this.inflater.avail_in > 0) {
               this.inflater.setOutput(this.buf, 0, this.buf.length);
               err = this.inflater.inflate(this.flush);
               if (this.inflater.next_out_index > 0) {
                  this.out.write(this.buf, 0, this.inflater.next_out_index);
               }

               if (err != 0) {
                  break;
               }
            }

            if (err != 0) {
               throw new ZStreamException("inflating: " + this.inflater.msg);
            }
         }
      }
   }

   public int getFlushMode() {
      return this.flush;
   }

   public void setFlushMode(int flush) {
      this.flush = flush;
   }

   public void finish() throws IOException {
      if (this.compress) {
         int tmp = this.flush;
         int flush = 4;

         try {
            this.write("".getBytes(), 0, 0);
         } finally {
            ;
         }
      } else {
         this.dos.finish();
      }

      this.flush();
   }

   public synchronized void end() {
      if (!this.end) {
         if (this.compress) {
            try {
               this.dos.finish();
            } catch (Exception e) {
            }
         } else {
            this.inflater.end();
         }

         this.end = true;
      }
   }

   @Override
   public void close() throws IOException {
      try {
         this.finish();
      } catch (IOException ignored) {
      } finally {
         this.end();
         this.out.close();
         this.out = null;
      }
   }

   public long getTotalIn() {
      return this.compress ? this.dos.getTotalIn() : this.inflater.total_in;
   }

   public long getTotalOut() {
      return this.compress ? this.dos.getTotalOut() : this.inflater.total_out;
   }

   @Override
   public void flush() throws IOException {
      this.out.flush();
   }
}
