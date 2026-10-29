package org.teavm.backend.javascript.templating;

import java.util.HashMap;
import java.util.Map;
import org.teavm.backend.javascript.ast.AstVisitor;
import org.teavm.rhino.javascript.ast.FunctionNode;

public class TemplatingFunctionIndex extends AstVisitor {
   private Map<String, FunctionNode> functions = new HashMap<>();

   @Override
   public void visit(FunctionNode node) {
      if (node.getName() != null) {
         this.functions.put(node.getName(), node);
      }
   }

   public FunctionNode getFunction(String name) {
      return this.functions.get(name);
   }
}
