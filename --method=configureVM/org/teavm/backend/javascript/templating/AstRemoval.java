package org.teavm.backend.javascript.templating;

import java.util.Iterator;
import java.util.Set;
import org.teavm.backend.javascript.ast.AstVisitor;
import org.teavm.rhino.javascript.ast.AstNode;
import org.teavm.rhino.javascript.ast.ExpressionStatement;
import org.teavm.rhino.javascript.ast.VariableDeclaration;
import org.teavm.rhino.javascript.ast.VariableInitializer;

public class AstRemoval extends AstVisitor {
   private Set<AstNode> nodes;

   public AstRemoval(Set<AstNode> nodes) {
      this.nodes = nodes;
   }

   @Override
   public void visit(ExpressionStatement node) {
      if (this.nodes.contains(node.getExpression())) {
         this.replaceWith(null);
      } else {
         super.visit(node);
      }
   }

   @Override
   public void visit(VariableDeclaration node) {
      Iterator<VariableInitializer> iter = node.getVariables().iterator();

      while (iter.hasNext()) {
         VariableInitializer initializer = iter.next();
         if (this.nodes.contains(initializer)) {
            iter.remove();
         }
      }

      if (node.getVariables().isEmpty()) {
         this.replaceWith(null);
      }
   }
}
