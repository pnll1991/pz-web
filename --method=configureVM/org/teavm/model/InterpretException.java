package org.teavm.model;

public class InterpretException extends Exception {
   private final BasicBlockReader block;

   public InterpretException(BasicBlockReader block, Throwable cause) {
      super(cause);
      this.block = block;
   }

   public BasicBlockReader getBlock() {
      return this.block;
   }
}
