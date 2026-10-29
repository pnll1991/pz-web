package org.teavm.ast;

import java.util.ArrayList;
import java.util.List;

public class TryCatchStatement extends Statement {
   private List<Statement> protectedBody = new ArrayList<>();
   private List<Statement> handler = new ArrayList<>();
   private String exceptionType;
   private Integer exceptionVariable;
   private boolean async;

   public List<Statement> getProtectedBody() {
      return this.protectedBody;
   }

   public List<Statement> getHandler() {
      return this.handler;
   }

   public String getExceptionType() {
      return this.exceptionType;
   }

   public void setExceptionType(String exceptionType) {
      this.exceptionType = exceptionType;
   }

   public Integer getExceptionVariable() {
      return this.exceptionVariable;
   }

   public void setExceptionVariable(Integer exceptionVariable) {
      this.exceptionVariable = exceptionVariable;
   }

   public boolean isAsync() {
      return this.async;
   }

   public void setAsync(boolean async) {
      this.async = async;
   }

   @Override
   public void acceptVisitor(StatementVisitor visitor) {
      visitor.visit(this);
   }
}
