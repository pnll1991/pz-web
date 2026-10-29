package org.teavm.ast;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public abstract class Statement {
   public abstract void acceptVisitor(StatementVisitor var1);

   public static Statement empty() {
      return new SequentialStatement();
   }

   public static AssignmentStatement assign(Expr left, Expr right) {
      AssignmentStatement stmt = new AssignmentStatement();
      stmt.setLeftValue(left);
      stmt.setRightValue(right);
      return stmt;
   }

   public static AssignmentStatement statementExpr(Expr expr) {
      AssignmentStatement stmt = new AssignmentStatement();
      stmt.setRightValue(expr);
      return stmt;
   }

   public static ReturnStatement exitFunction() {
      return exitFunction(null);
   }

   public static ReturnStatement exitFunction(Expr result) {
      ReturnStatement stmt = new ReturnStatement();
      stmt.setResult(result);
      return stmt;
   }

   public static SequentialStatement sequence(Statement... statements) {
      SequentialStatement seq = new SequentialStatement();
      seq.getSequence().addAll(Arrays.asList(statements));
      return seq;
   }

   public static BlockStatement block(Function<BlockStatement, Collection<Statement>> body) {
      BlockStatement statement = new BlockStatement();
      statement.getBody().addAll(body.apply(statement));
      return statement;
   }

   public static BreakStatement exitBlock(IdentifiedStatement identifiedStatement) {
      BreakStatement statement = new BreakStatement();
      statement.setTarget(identifiedStatement);
      return statement;
   }

   public static WhileStatement loopWhile(Expr condition, Function<WhileStatement, Collection<Statement>> body) {
      WhileStatement statement = new WhileStatement();
      statement.setCondition(condition);
      statement.getBody().addAll(body.apply(statement));
      return statement;
   }

   public static ThrowStatement raiseException(Expr exception) {
      ThrowStatement stmt = new ThrowStatement();
      stmt.setException(exception);
      return stmt;
   }

   public static Statement cond(Expr predicate, List<Statement> consequent, List<Statement> alternative) {
      ConditionalStatement statement = new ConditionalStatement();
      statement.setCondition(predicate);
      statement.getConsequent().addAll(consequent);
      statement.getAlternative().addAll(alternative);
      return statement;
   }

   public static Statement cond(Expr predicate, List<Statement> consequent) {
      return cond(predicate, consequent, Collections.emptyList());
   }

   public static InitClassStatement initClass(String className) {
      InitClassStatement stmt = new InitClassStatement();
      stmt.setClassName(className);
      return stmt;
   }

   public static SwitchStatement switchStatement(Expr condition, List<Statement> defaultClause, SwitchClause... clauses) {
      SwitchStatement statement = new SwitchStatement();
      statement.setValue(condition);
      statement.getDefaultClause().addAll(defaultClause);
      statement.getClauses().addAll(List.of(clauses));
      return statement;
   }

   public static SwitchClause switchClause(int[] conditions, Statement... statements) {
      SwitchClause clause = new SwitchClause();
      clause.setConditions(conditions);
      clause.getBody().addAll(List.of(statements));
      return clause;
   }

   public static SwitchClause switchClause(int condition, Statement... statements) {
      return switchClause(new int[]{condition}, statements);
   }

   public static Statement.TryCatchStatementBuilder doTry(Statement... statements) {
      return new Statement.TryCatchStatementBuilder(statements);
   }

   public static class TryCatchStatementBuilder {
      Statement[] protectedBody;

      TryCatchStatementBuilder(Statement[] protectedBody) {
         this.protectedBody = protectedBody;
      }

      public Statement.TryCatchStatementBuilder2 doCatch(String type, Integer variable) {
         TryCatchStatement result = new TryCatchStatement();
         result.getProtectedBody().addAll(Arrays.asList(this.protectedBody));
         result.setExceptionType(type);
         result.setExceptionVariable(variable);
         return new Statement.TryCatchStatementBuilder2(result);
      }
   }

   public static class TryCatchStatementBuilder2 {
      TryCatchStatement statement;

      TryCatchStatementBuilder2(TryCatchStatement statement) {
         this.statement = statement;
      }

      public TryCatchStatement with(Statement... statements) {
         this.statement.getHandler().addAll(Arrays.asList(statements));
         return this.statement;
      }
   }
}
