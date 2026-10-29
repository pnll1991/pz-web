package org.teavm.common.binary;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Blob {
   private byte[] buffer = new byte[16];
   byte[] currentChunk = new byte[4096];
   List<byte[]> data = new ArrayList<>();
   int chunkIndex;
   int posInChunk;
   int ptr;
   private int size;

   public Blob() {
      this.data.add(this.currentChunk);
   }

   public Blob write(byte[] bytes) {
      return this.write(bytes, 0, bytes.length);
   }

   public Blob write(byte[] bytes, int offset, int limit) {
      if (offset == limit) {
         return this;
      }

      if (offset + 1 == limit) {
         this.write(bytes[offset]);
         return this;
      }

      while (offset < limit) {
         int remaining = Math.min(limit - offset, this.currentChunk.length - this.posInChunk);
         System.arraycopy(bytes, offset, this.currentChunk, this.posInChunk, remaining);
         this.posInChunk += remaining;
         offset += remaining;
         this.ptr += remaining;
         this.nextChunkIfNeeded();
      }

      this.size = Math.max(this.size, this.ptr);
      return this;
   }

   public Blob skip(int count) {
      while (count > 0) {
         int remaining = Math.min(count, this.currentChunk.length - this.posInChunk);
         this.posInChunk += remaining;
         this.ptr += remaining;
         count -= remaining;
         this.nextChunkIfNeeded();
      }

      this.size = Math.max(this.size, this.ptr);
      return this;
   }

   public Blob write(byte b) {
      this.currentChunk[this.posInChunk++] = b;
      this.ptr++;
      this.nextChunkIfNeeded();
      this.size = Math.max(this.size, this.ptr);
      return this;
   }

   public Blob writeInt(int value) {
      byte[] buffer = this.buffer;
      buffer[0] = (byte)value;
      buffer[1] = (byte)(value >>> 8);
      buffer[2] = (byte)(value >>> 16);
      buffer[3] = (byte)(value >>> 24);
      return this.write(buffer, 0, 4);
   }

   public Blob writeShort(int value) {
      byte[] buffer = this.buffer;
      buffer[0] = (byte)value;
      buffer[1] = (byte)(value >>> 8);
      return this.write(buffer, 0, 2);
   }

   public Blob writeByte(int value) {
      return this.write((byte)value);
   }

   public Blob writeLEB(int value) {
      int ptr = 0;
      byte[] buffer = this.buffer;

      while ((value & 127) != value) {
         buffer[ptr++] = (byte)(value & 127 | 128);
         value >>>= 7;
      }

      buffer[ptr++] = (byte)(value & 127);
      return this.write(buffer, 0, ptr);
   }

   public Blob writeSLEB(int value) {
      int ptr = 0;
      byte[] buffer = this.buffer;
      int sign = value >>> 31;

      while (true) {
         int digit = value & 127;
         value >>= 7;
         boolean more = value != 0 && value != -1 || digit >> 6 != sign;
         if (!more) {
            buffer[ptr++] = (byte)digit;
            return this.write(buffer, 0, ptr);
         }

         buffer[ptr++] = (byte)(digit | 128);
      }
   }

   public Blob writeString(String value) {
      this.writeLEB(value.length());
      this.write(value.getBytes(StandardCharsets.UTF_8));
      return this;
   }

   private void nextChunkIfNeeded() {
      if (this.posInChunk >= this.currentChunk.length) {
         this.posInChunk = 0;
         if (++this.chunkIndex >= this.data.size()) {
            this.currentChunk = new byte[this.currentChunk.length];
            this.data.add(this.currentChunk);
         } else {
            this.currentChunk = this.data.get(this.chunkIndex);
         }
      }
   }

   public int chunkCount() {
      return this.data.size() + 1;
   }

   public BlobReader newReader(BinaryDataConsumer consumer) {
      return new BlobReader(this, consumer);
   }

   public BinaryDataConsumer writer() {
      return this::write;
   }

   public Marker marker() {
      return new Marker(this, this.chunkIndex, this.posInChunk, this.ptr);
   }

   public byte[] chunkAt(int index) {
      return index < this.data.size() ? this.data.get(index) : this.currentChunk;
   }

   public int ptr() {
      return this.ptr;
   }

   public int size() {
      return this.size;
   }

   public byte[] toArray() {
      byte[] result = new byte[this.size];
      int ptr = 0;

      for (byte[] chunk : this.data) {
         int bytesToCopy = Math.min(chunk.length, this.size - ptr);
         if (bytesToCopy > 0) {
            System.arraycopy(chunk, 0, result, ptr, bytesToCopy);
            ptr += bytesToCopy;
         }
      }

      return result;
   }
}
