package org.teavm.backend.javascript.rendering;

import org.teavm.rhino.javascript.Node;
import org.teavm.rhino.javascript.ast.AstNode;
import org.teavm.rhino.javascript.ast.Block;
import org.teavm.rhino.javascript.ast.ExpressionStatement;
import org.teavm.rhino.javascript.ast.NodeVisitor;
import org.teavm.rhino.javascript.ast.Scope;
import org.teavm.rhino.javascript.ast.StringLiteral;

public class StringConstantElimination implements NodeVisitor {
   public boolean visit(AstNode astNode) {
      if (astNode instanceof Block || astNode instanceof Scope) {
         this.handle(astNode);
      }

      return true;
   }

   private void handle(AstNode block) {
      Node child = block.getFirstChild();

      while (child != null) {
         Node next = child.getNext();
         if (child instanceof ExpressionStatement statement && statement.getExpression() instanceof StringLiteral) {
            block.removeChild(child);
         }

         child = next;
      }
   }
}
