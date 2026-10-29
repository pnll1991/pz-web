package org.teavm.common.binary;

public class Marker {
   private Blob blob;
   private int chunkIndex;
   private int posInChunk;
   private int ptr;

   Marker(Blob blob, int chunkIndex, int posInChunk, int ptr) {
      this.blob = blob;
      this.chunkIndex = chunkIndex;
      this.posInChunk = posInChunk;
      this.ptr = ptr;
   }

   public void rewind() {
      this.blob.chunkIndex = this.chunkIndex;
      this.blob.posInChunk = this.posInChunk;
      this.blob.ptr = this.ptr;
      this.blob.currentChunk = this.blob.data.get(this.chunkIndex);
   }

   public void update() {
      this.chunkIndex = this.blob.chunkIndex;
      this.posInChunk = this.blob.posInChunk;
      this.ptr = this.blob.ptr;
   }

   public int ptr() {
      return this.ptr;
   }
}
