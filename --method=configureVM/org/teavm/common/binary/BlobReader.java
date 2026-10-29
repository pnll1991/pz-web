package org.teavm.common.binary;

public class BlobReader {
   private Blob blob;
   private BinaryDataConsumer consumer;
   private int ptr;
   private int currentChunk;
   private int offsetInChunk;

   BlobReader(Blob blob, BinaryDataConsumer consumer) {
      this.blob = blob;
      this.consumer = consumer;
   }

   public int position() {
      return this.ptr;
   }

   public void readRemaining() {
      this.advance(this.blob.size());
   }

   public void advance(int to) {
      if (to >= this.ptr && to <= this.blob.size()) {
         if (to != this.ptr) {
            int ptr = this.ptr;
            int currentChunk = this.currentChunk;
            int offsetInChunk = this.offsetInChunk;

            while (ptr < to) {
               byte[] chunk = this.blob.chunkAt(currentChunk);
               int limit = Math.min(ptr + chunk.length - offsetInChunk, to);
               int bytesToWrite = limit - ptr;
               this.consumer.accept(chunk, offsetInChunk, offsetInChunk + bytesToWrite);
               offsetInChunk += bytesToWrite;
               ptr += bytesToWrite;
               if (offsetInChunk == chunk.length) {
                  offsetInChunk = 0;
                  currentChunk++;
               }
            }

            this.ptr = ptr;
            this.currentChunk = currentChunk;
            this.offsetInChunk = offsetInChunk;
         }
      } else {
         throw new IllegalArgumentException();
      }
   }
}
