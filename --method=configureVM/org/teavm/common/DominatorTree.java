package org.teavm.common;

public interface DominatorTree {
   boolean directlyDominates(int var1, int var2);

   int commonDominatorOf(int var1, int var2);

   boolean dominates(int var1, int var2);

   int immediateDominatorOf(int var1);

   int levelOf(int var1);

   default int commonDominatorOf(int[] nodes) {
      if (nodes.length == 0) {
         return -1;
      }

      int result = nodes[0];

      for (int i = 1; i < nodes.length; i++) {
         result = this.commonDominatorOf(result, nodes[i]);
      }

      return result;
   }
}
