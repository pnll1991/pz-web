package org.teavm.model;

public class TryCatchBlock implements TryCatchBlockReader {
   BasicBlock protectedBlock;
   private BasicBlock handler;
   private String exceptionType;

   public BasicBlock getHandler() {
      return this.handler;
   }

   public void setHandler(BasicBlock handler) {
      this.handler = handler;
   }

   @Override
   public String getExceptionType() {
      return this.exceptionType;
   }

   public void setExceptionType(String exceptionType) {
      this.exceptionType = exceptionType;
   }

   public BasicBlock getProtectedBlock() {
      return this.protectedBlock;
   }
}
