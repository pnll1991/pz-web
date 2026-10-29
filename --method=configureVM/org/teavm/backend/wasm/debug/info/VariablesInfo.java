package org.teavm.backend.wasm.debug.info;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class VariablesInfo {
   public abstract List<? extends VariableRangeInfo> ranges();

   public Collection<? extends VariableRangeInfo> find(int address) {
      ArrayList<VariableRangeInfo> result = new ArrayList<>();

      for (VariableRangeInfo range : this.ranges()) {
         if (address >= range.start() && address < range.end()) {
            result.add(range);
         }
      }

      return result;
   }

   public void dump(PrintStream out) {
      for (VariableRangeInfo range : this.ranges()) {
         out.println(
            range.variable().name()
               + ": "
               + range.variable().type()
               + " - "
               + Integer.toHexString(range.start())
               + ".."
               + Integer.toHexString(range.end())
               + " at "
               + range.index()
         );
      }
   }
}
