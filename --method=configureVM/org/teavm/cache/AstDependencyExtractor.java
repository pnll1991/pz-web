package org.teavm.cache;

import java.util.HashSet;
import java.util.Set;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.AsyncMethodPart;
import org.teavm.ast.InvocationExpr;
import org.teavm.ast.QualificationExpr;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.RegularMethodNode;

public class AstDependencyExtractor extends RecursiveVisitor {
   private final AstDependencyExtractor.ExtractingVisitor visitor = new AstDependencyExtractor.ExtractingVisitor();

   public String[] extract(RegularMethodNode node) {
      node.getBody().acceptVisitor(this.visitor);
      String[] result = this.visitor.dependencies.toArray(new String[0]);
      this.visitor.dependencies.clear();
      return result;
   }

   public String[] extract(AsyncMethodNode node) {
      for (AsyncMethodPart part : node.getBody()) {
         part.getStatement().acceptVisitor(this.visitor);
      }

      String[] result = this.visitor.dependencies.toArray(new String[0]);
      this.visitor.dependencies.clear();
      return result;
   }

   static final class ExtractingVisitor extends RecursiveVisitor {
      final Set<String> dependencies = new HashSet<>();

      @Override
      public void visit(InvocationExpr expr) {
         super.visit(expr);
         this.dependencies.add(expr.getMethod().getClassName());
      }

      @Override
      public void visit(QualificationExpr expr) {
         super.visit(expr);
         this.dependencies.add(expr.getField().getClassName());
      }
   }
}
