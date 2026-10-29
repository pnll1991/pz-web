package org.teavm.model.util;

import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;

public interface DominatorWalkerContext {
   DominatorTree getDominatorTree();

   Graph getControlFlowGraph();

   boolean isVisited(int var1);

   boolean isExceptionHandler(int var1);

   default boolean allPredecessorsVisited(int blockIndex) {
      for (int predecessor : this.getControlFlowGraph().incomingEdges(blockIndex)) {
         if (!this.isVisited(predecessor)) {
            return false;
         }
      }

      return true;
   }
}
