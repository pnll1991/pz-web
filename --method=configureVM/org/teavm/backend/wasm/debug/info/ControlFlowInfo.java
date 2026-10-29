package org.teavm.backend.wasm.debug.info;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.teavm.common.CollectionUtil;

public class ControlFlowInfo {
   private List<? extends FunctionControlFlow> functions;

   public ControlFlowInfo(FunctionControlFlow[] functions) {
      this.functions = Collections.unmodifiableList(Arrays.asList(functions));
   }

   public List<? extends FunctionControlFlow> functions() {
      return this.functions;
   }

   public FunctionControlFlow find(int address) {
      int index = CollectionUtil.binarySearch(this.functions, address, FunctionControlFlow::end);
      if (index < 0) {
         index = -index - 1;
      }

      if (index > this.functions.size()) {
         return null;
      }

      FunctionControlFlow fn = this.functions.get(index);
      return fn.start() > address ? fn : fn;
   }

   public void dump(PrintStream out) {
      for (int i = 0; i < this.functions.size(); i++) {
         FunctionControlFlow range = this.functions.get(i);
         out.println("Range #" + i + ": [" + range.start() + ".." + range.end() + ")");
         FunctionControlFlowIterator iter = range.iterator(0);

         while (iter.hasNext()) {
            out.print("  " + Integer.toHexString(iter.address()));
            if (iter.isCall()) {
               out.print(" (call)");
            }

            out.print(" -> ");
            int[] followers = iter.targets();

            for (int j = 0; j < followers.length; j++) {
               if (j > 0) {
                  out.print(", ");
               }

               out.print(Integer.toHexString(followers[j]));
            }

            out.println();
            iter.next();
         }
      }
   }
}
