package org.teavm.model.util;

import org.teavm.model.BasicBlock;

public interface DominatorWalkerCallback<T> {
   default void setContext(DominatorWalkerContext context) {
   }

   default boolean filter(BasicBlock block) {
      return true;
   }

   T visit(BasicBlock var1);

   default void endVisit(BasicBlock block, T state) {
   }
}
