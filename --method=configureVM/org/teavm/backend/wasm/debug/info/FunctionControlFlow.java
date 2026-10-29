package org.teavm.backend.wasm.debug.info;

public class FunctionControlFlow {
   private int start;
   private int end;
   int[] offsets;
   int[] data;

   FunctionControlFlow(int start, int end, int[] offsets, int[] data) {
      this.start = start;
      this.end = end;
      this.offsets = offsets;
      this.data = data;
   }

   public int start() {
      return this.start;
   }

   public int end() {
      return this.end;
   }

   public FunctionControlFlowIterator iterator(int index) {
      return new FunctionControlFlowIterator(this, index);
   }

   public int count() {
      return this.offsets.length;
   }

   public int findIndex(int address) {
      int l = 0;
      int u = this.offsets.length;

      while (true) {
         int i = (l + u) / 2;
         int t = this.data[this.offsets[i]];
         if (address == t) {
            return i;
         }

         if (address > t) {
            l = i + 1;
            if (l > u) {
               return i + 1;
            }
         } else {
            u = i - 1;
            if (u < l) {
               return i;
            }
         }
      }
   }
}
