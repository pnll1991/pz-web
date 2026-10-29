package org.teavm.common;

import java.util.function.Supplier;

public class ByteArrayAsyncInputStream implements AsyncInputStream {
   private byte[] data;
   private int ptr;
   private CompletablePromise<Integer> promise;
   private byte[] target;
   private int offset;
   private int count;
   private Throwable errorOccurred;

   public ByteArrayAsyncInputStream(byte[] data) {
      this.data = data;
   }

   @Override
   public Promise<Integer> skip(int amount) {
      this.promise = new CompletablePromise<>();
      this.count = amount;
      return this.promise;
   }

   @Override
   public Promise<Integer> read(byte[] buffer, int offset, int count) {
      this.promise = new CompletablePromise<>();
      this.target = buffer;
      this.offset = offset;
      this.count = count;
      return this.promise;
   }

   public void readFully(Supplier<Promise<?>> command) {
      Promise.runNow(() -> {
         command.get().catchError(ex -> {
            this.errorOccurred = ex;
            throw new RuntimeException(ex);
         });

         while (this.promise != null) {
            CompletablePromise<Integer> p = this.promise;
            this.count = Math.min(this.count, this.data.length - this.ptr);
            this.promise = null;
            if (this.target != null) {
               System.arraycopy(this.data, this.ptr, this.target, this.offset, this.count);
               this.target = null;
            }

            this.ptr = this.ptr + this.count;
            if (this.count == 0) {
               this.count = -1;
            }

            p.complete(this.count);
         }
      });
      Throwable e = this.errorOccurred;
      this.errorOccurred = null;
      if (e != null) {
         throw new RuntimeException(e);
      }
   }
}
