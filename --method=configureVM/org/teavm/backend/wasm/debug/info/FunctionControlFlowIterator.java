package org.teavm.backend.wasm.debug.info;

import java.util.Arrays;

public class FunctionControlFlowIterator {
   private FunctionControlFlow controlFlow;
   private int index;
   private boolean valid;
   private int offset;
   private boolean isCall;

   FunctionControlFlowIterator(FunctionControlFlow controlFlow, int index) {
      this.controlFlow = controlFlow;
      this.index = index;
   }

   public boolean hasNext() {
      return this.index < this.controlFlow.offsets.length;
   }

   public void next() {
      this.index++;
      this.valid = false;
   }

   public void rewind(int index) {
      if (this.index != index) {
         this.index = index;
         this.fill();
      }
   }

   private void fill() {
      if (!this.valid) {
         this.valid = true;
         int n = this.controlFlow.offsets[this.index];
         this.offset = n >>> 1;
         this.isCall = (n & 1) != 0;
      }
   }

   public int address() {
      this.fill();
      return this.controlFlow.data[this.offset];
   }

   public int[] targets() {
      this.fill();
      int nextOffset = this.index < this.controlFlow.offsets.length - 1 ? this.controlFlow.offsets[this.index + 1] >>> 1 : this.controlFlow.data.length;
      return Arrays.copyOfRange(this.controlFlow.data, this.offset + 1, nextOffset);
   }

   public boolean isCall() {
      this.fill();
      return this.isCall;
   }
}
