package org.teavm.ast;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.MethodReference;

public class RegularMethodNode extends MethodNode {
   private Statement body;
   private List<VariableNode> variables = new ArrayList<>();

   public RegularMethodNode(MethodReference reference) {
      super(reference);
   }

   public Statement getBody() {
      return this.body;
   }

   public void setBody(Statement body) {
      this.body = body;
   }

   @Override
   public List<VariableNode> getVariables() {
      return this.variables;
   }

   @Override
   public void acceptVisitor(MethodNodeVisitor visitor) {
      visitor.visit(this);
   }

   @Override
   public boolean isAsync() {
      return false;
   }
}
