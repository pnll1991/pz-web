package org.teavm.backend.wasm.debug.info;

import org.teavm.hppc.IntArrayList;

public class FunctionControlFlowBuilder {
   private int start;
   private int end;
   private IntArrayList offsets = new IntArrayList();
   private IntArrayList data = new IntArrayList();

   public FunctionControlFlowBuilder(int start, int end) {
      this.start = start;
      this.end = end;
   }

   public void addBranch(int position, int[] targets) {
      this.offsets.add(this.data.size() << 1);
      this.data.add(position);
      this.data.add(targets);
   }

   public void addCall(int position, int[] targets) {
      this.offsets.add(this.data.size() << 1 | 1);
      this.data.add(position);
      this.data.add(targets);
   }

   public boolean isEmpty() {
      return this.offsets.isEmpty();
   }

   public FunctionControlFlow build() {
      return new FunctionControlFlow(this.start, this.end, this.offsets.toArray(), this.data.toArray());
   }
}
