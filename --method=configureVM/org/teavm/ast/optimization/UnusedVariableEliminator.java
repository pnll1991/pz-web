package org.teavm.ast.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.TryCatchStatement;
import org.teavm.ast.VariableExpr;
import org.teavm.ast.VariableNode;

class UnusedVariableEliminator extends RecursiveVisitor {
   private final VariableNode[] variableNodes;
   private final int[] variables;
   private final int[] indexes;
   private List<VariableNode> reorderedVariables = new ArrayList<>();
   int lastIndex;

   UnusedVariableEliminator(int parameterCount, List<VariableNode> variables) {
      this.variableNodes = new VariableNode[variables.size()];
      this.variables = new int[variables.size()];
      int variableCount = 0;

      for (int i = 0; i < variables.size(); i++) {
         this.variableNodes[i] = variables.get(i);
         int var = variables.get(i).getIndex();
         this.variables[i] = var;
         variableCount = Math.max(variableCount, var + 1);
      }

      this.indexes = new int[variableCount];
      Arrays.fill(this.indexes, -1);
      parameterCount = Math.min(parameterCount, this.indexes.length - 1);

      for (int i = 0; i <= parameterCount; i++) {
         this.indexes[i] = this.lastIndex++;
         this.reorderedVariables.add(this.variableNodes[i]);
      }
   }

   public List<VariableNode> getReorderedVariables() {
      return this.reorderedVariables;
   }

   private int renumber(int var) {
      int index = this.indexes[this.variables[var]];
      if (index == -1) {
         index = this.lastIndex++;
         this.indexes[this.variables[var]] = index;
         this.reorderedVariables.add(this.variableNodes[var]);
      }

      VariableNode node = this.variableNodes[var];
      if (node.getName() != null) {
         this.reorderedVariables.get(index).setName(node.getName());
      }

      return index;
   }

   @Override
   public void visit(VariableExpr expr) {
      expr.setIndex(this.renumber(expr.getIndex()));
   }

   @Override
   public void visit(TryCatchStatement statement) {
      super.visit(statement);
      if (statement.getExceptionVariable() != null) {
         if (this.variables[statement.getExceptionVariable()] < 0) {
            statement.setExceptionVariable(null);
         } else {
            statement.setExceptionVariable(this.renumber(statement.getExceptionVariable()));
         }
      }
   }
}
