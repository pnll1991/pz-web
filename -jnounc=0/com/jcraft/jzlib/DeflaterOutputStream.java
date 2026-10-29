package com.jcraft.jzlib;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DeflaterOutputStream extends FilterOutputStream {
   protected final Deflater deflater;
   protected byte[] buffer;
   private boolean closed = false;
   private boolean syncFlush = false;
   private final byte[] buf1 = new byte[1];
   protected boolean mydeflater = false;
   private boolean close_out = true;
   protected static final int DEFAULT_BUFSIZE = 512;

   public DeflaterOutputStream(OutputStream out) throws IOException {
      this(out, new Deflater(-1), 512, true);
      this.mydeflater = true;
   }

   public DeflaterOutputStream(OutputStream out, Deflater def) throws IOException {
      this(out, def, 512, true);
   }

   public DeflaterOutputStream(OutputStream out, Deflater deflater, int size) throws IOException {
      this(out, deflater, size, true);
   }

   public DeflaterOutputStream(OutputStream out, Deflater deflater, int size, boolean close_out) throws IOException {
      super(out);
      if (out == null || deflater == null) {
         throw new NullPointerException();
      }

      if (size <= 0) {
         throw new IllegalArgumentException("buffer size must be greater than 0");
      }

      this.deflater = deflater;
      this.buffer = new byte[size];
      this.close_out = close_out;
   }

   @Override
   public void write(int b) throws IOException {
      this.buf1[0] = (byte)(b & 0xFF);
      this.write(this.buf1, 0, 1);
   }

   @Override
   public void write(byte[] b, int off, int len) throws IOException {
      if (this.deflater.finished()) {
         throw new IOException("finished");
      }

      if (off < 0 | len < 0 | off + len > b.length) {
         throw new IndexOutOfBoundsException();
      }

      if (len != 0) {
         int flush = this.syncFlush ? 2 : 0;
         this.deflater.setInput(b, off, len, true);

         while (this.deflater.avail_in > 0) {
            int err = this.deflate(flush);
            if (err == 1) {
               break;
            }
         }
      }
   }

   public void finish() throws IOException {
      while (!this.deflater.finished()) {
         this.deflate(4);
      }
   }

   @Override
   public void close() throws IOException {
      if (!this.closed) {
         this.finish();
         if (this.mydeflater) {
            this.deflater.end();
         }

         if (this.close_out) {
            this.out.close();
         }

         this.closed = true;
      }
   }

   protected int deflate(int flush) throws IOException {
      this.deflater.setOutput(this.buffer, 0, this.buffer.length);
      int err = this.deflater.deflate(flush);
      switch (err) {
         case -5:
            if (this.deflater.avail_in > 0 || flush == 4) {
               break;
            }
         case 0:
         case 1:
            int len = this.deflater.next_out_index;
            if (len > 0) {
               this.out.write(this.buffer, 0, len);
            }

            return err;
      }

      throw new IOException("failed to deflate");
   }

   @Override
   public void flush() throws IOException {
      int err;
      if (this.syncFlush && !this.deflater.finished()) {
         do {
            err = this.deflate(2);
         } while (this.deflater.next_out_index >= this.buffer.length && err != 1);
      }

      this.out.flush();
   }

   public long getTotalIn() {
      return this.deflater.getTotalIn();
   }

   public long getTotalOut() {
      return this.deflater.getTotalOut();
   }

   public void setSyncFlush(boolean syncFlush) {
      this.syncFlush = syncFlush;
   }

   public boolean getSyncFlush() {
      return this.syncFlush;
   }

   public Deflater getDeflater() {
      return this.deflater;
   }
}
