package com.jcraft.jzlib;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class InflaterInputStream extends FilterInputStream {
   protected final Inflater inflater;
   protected byte[] buf;
   private boolean closed = false;
   private boolean eof = false;
   private boolean close_in = true;
   protected static final int DEFAULT_BUFSIZE = 512;
   protected boolean myinflater = false;
   private byte[] byte1 = new byte[1];
   private byte[] b = new byte[512];

   public InflaterInputStream(InputStream in) throws IOException {
      this(in, false);
   }

   public InflaterInputStream(InputStream in, boolean nowrap) throws IOException {
      this(in, new Inflater(nowrap));
      this.myinflater = true;
   }

   public InflaterInputStream(InputStream in, Inflater inflater) throws IOException {
      this(in, inflater, 512);
   }

   public InflaterInputStream(InputStream in, Inflater inflater, int size) throws IOException {
      this(in, inflater, size, true);
   }

   public InflaterInputStream(InputStream in, Inflater inflater, int size, boolean close_in) throws IOException {
      super(in);
      if (in == null || inflater == null) {
         throw new NullPointerException();
      }

      if (size <= 0) {
         throw new IllegalArgumentException("buffer size must be greater than 0");
      }

      this.inflater = inflater;
      this.buf = new byte[size];
      this.close_in = close_in;
   }

   @Override
   public int read() throws IOException {
      if (this.closed) {
         throw new IOException("Stream closed");
      } else {
         return this.read(this.byte1, 0, 1) == -1 ? -1 : this.byte1[0] & 0xFF;
      }
   }

   @Override
   public int read(byte[] b, int off, int len) throws IOException {
      if (this.closed) {
         throw new IOException("Stream closed");
      }

      if (b == null) {
         throw new NullPointerException();
      }

      if (off < 0 || len < 0 || len > b.length - off) {
         throw new IndexOutOfBoundsException();
      }

      if (len == 0) {
         return 0;
      }

      if (this.eof) {
         return -1;
      }

      int n = 0;
      this.inflater.setOutput(b, off, len);

      while (!this.eof) {
         if (this.inflater.avail_in == 0) {
            this.fill();
         }

         int err = this.inflater.inflate(0);
         n += this.inflater.next_out_index - off;
         off = this.inflater.next_out_index;
         switch (err) {
            case -3:
               throw new IOException(this.inflater.msg);
            case 1:
            case 2:
               this.eof = true;
               if (err == 2) {
                  return -1;
               }
         }

         if (this.inflater.avail_out == 0) {
            break;
         }
      }

      return n;
   }

   @Override
   public int available() throws IOException {
      if (this.closed) {
         throw new IOException("Stream closed");
      } else {
         return this.eof ? 0 : 1;
      }
   }

   @Override
   public long skip(long n) throws IOException {
      if (n < 0L) {
         throw new IllegalArgumentException("negative skip length");
      }

      if (this.closed) {
         throw new IOException("Stream closed");
      }

      int max = (int)Math.min(n, 2147483647L);
      int total = 0;

      while (total < max) {
         int len = max - total;
         if (len > this.b.length) {
            len = this.b.length;
         }

         len = this.read(this.b, 0, len);
         if (len == -1) {
            this.eof = true;
            break;
         }

         total += len;
      }

      return total;
   }

   @Override
   public void close() throws IOException {
      if (!this.closed) {
         if (this.myinflater) {
            this.inflater.end();
         }

         if (this.close_in) {
            this.in.close();
         }

         this.closed = true;
      }
   }

   protected void fill() throws IOException {
      if (this.closed) {
         throw new IOException("Stream closed");
      }

      int len = this.in.read(this.buf, 0, this.buf.length);
      if (len == -1) {
         if (this.inflater.istate.wrap != 0 || this.inflater.finished()) {
            if (this.inflater.istate.was != -1L) {
               throw new IOException("footer is not found");
            }

            throw new EOFException("Unexpected end of ZLIB input stream");
         }

         this.buf[0] = 0;
         len = 1;
      }

      this.inflater.setInput(this.buf, 0, len, true);
   }

   @Override
   public boolean markSupported() {
      return false;
   }

   @Override
   public synchronized void mark(int readlimit) {
   }

   @Override
   public synchronized void reset() throws IOException {
      throw new IOException("mark/reset not supported");
   }

   public long getTotalIn() {
      return this.inflater.getTotalIn();
   }

   public long getTotalOut() {
      return this.inflater.getTotalOut();
   }

   public byte[] getAvailIn() {
      if (this.inflater.avail_in <= 0) {
         return null;
      }

      byte[] tmp = new byte[this.inflater.avail_in];
      System.arraycopy(this.inflater.next_in, this.inflater.next_in_index, tmp, 0, this.inflater.avail_in);
      return tmp;
   }

   public void readHeader() throws IOException {
      byte[] empty = "".getBytes();
      this.inflater.setInput(empty, 0, 0, false);
      this.inflater.setOutput(empty, 0, 0);
      int err = this.inflater.inflate(0);
      if (this.inflater.istate.inParsingHeader()) {
         byte[] b1 = new byte[1];

         do {
            int i = this.in.read(b1);
            if (i <= 0) {
               throw new IOException("no input");
            }

            this.inflater.setInput(b1);
            err = this.inflater.inflate(0);
            if (err != 0) {
               throw new IOException(this.inflater.msg);
            }
         } while (this.inflater.istate.inParsingHeader());
      }
   }

   public Inflater getInflater() {
      return this.inflater;
   }
}
