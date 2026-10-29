package org.teavm.ast.optimization;

import org.teavm.ast.BlockStatement;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.RecursiveVisitor;

class BlockCountVisitor extends RecursiveVisitor {
   private BlockStatement blockToCount;
   private int count;

   BlockCountVisitor(BlockStatement blockToCount) {
      this.blockToCount = blockToCount;
   }

   public int getCount() {
      return this.count;
   }

   @Override
   public void visit(BreakStatement statement) {
      if (statement.getTarget() == this.blockToCount) {
         this.count++;
      }
   }

   @Override
   public void visit(ContinueStatement statement) {
      if (statement.getTarget() == this.blockToCount) {
         this.count++;
      }
   }
}
