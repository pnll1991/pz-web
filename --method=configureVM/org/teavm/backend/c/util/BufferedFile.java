package org.teavm.backend.c.util;

import java.io.IOException;
import java.io.RandomAccessFile;

class BufferedFile {
   private RandomAccessFile out;
   private byte[] buffer;
   private int pos;

   BufferedFile(RandomAccessFile out) {
      this.out = out;
      this.buffer = new byte[4096];
   }

   void flush() throws IOException {
      if (this.pos > 0) {
         this.out.write(this.buffer, 0, this.pos);
         this.pos = 0;
      }
   }

   void writeInt(int v) throws IOException {
      byte[] buffer = this.buffer;
      int localPos = this.pos;
      if (localPos + 4 >= buffer.length) {
         this.flush();
         localPos = this.pos;
      }

      buffer[localPos++] = (byte)(v >>> 24);
      buffer[localPos++] = (byte)(v >>> 16 & 0xFF);
      buffer[localPos++] = (byte)(v >>> 8 & 0xFF);
      buffer[localPos++] = (byte)(v & 0xFF);
      this.pos = localPos;
   }

   void writeShort(int v) throws IOException {
      byte[] buffer = this.buffer;
      int localPos = this.pos;
      if (localPos + 2 >= buffer.length) {
         this.flush();
         localPos = this.pos;
      }

      buffer[localPos++] = (byte)(v >>> 8 & 0xFF);
      buffer[localPos++] = (byte)(v & 0xFF);
      this.pos = localPos;
   }

   void write(byte[] data) throws IOException {
      this.write(data, 0, data.length);
   }

   void write(byte[] data, int start, int length) throws IOException {
      if (this.pos + length >= this.buffer.length) {
         this.flush();
      }

      if (length > this.buffer.length) {
         this.out.write(data, start, length);
      } else {
         System.arraycopy(data, start, this.buffer, this.pos, length);
         this.pos += length;
      }
   }

   void write(int b) throws IOException {
      if (this.pos + 1 >= this.buffer.length) {
         this.flush();
      }

      this.buffer[this.pos++] = (byte)b;
   }

   long getFilePointer() throws IOException {
      return this.out.getFilePointer() + this.pos;
   }

   void seek(long pointer) throws IOException {
      this.flush();
      this.out.seek(pointer);
   }
}
