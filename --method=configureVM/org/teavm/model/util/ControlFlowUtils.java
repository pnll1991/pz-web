package org.teavm.model.util;

import org.teavm.common.IntegerArray;
import org.teavm.common.Loop;
import org.teavm.common.LoopGraph;

public final class ControlFlowUtils {
   private ControlFlowUtils() {
   }

   public static int[][] findLoopExits(LoopGraph cfg) {
      IntegerArray[] loops = new IntegerArray[cfg.size()];

      for (Loop loop : cfg.knownLoops()) {
         loops[loop.getHead()] = new IntegerArray(4);
      }

      for (int node = 0; node < cfg.size(); node++) {
         for (Loop loop = cfg.loopAt(node); loop != null; loop = loop.getParent()) {
            for (int successor : cfg.outgoingEdges(node)) {
               Loop successorLoop = cfg.loopAt(successor);
               if (successorLoop == null || !successorLoop.isChildOf(loop)) {
                  loops[loop.getHead()].add(node);
                  break;
               }
            }
         }
      }

      int[][] result = new int[cfg.size()][];

      for (int i = 0; i < loops.length; i++) {
         IntegerArray builder = loops[i];
         if (builder != null) {
            result[i] = builder.getAll();
         }
      }

      return result;
   }
}
