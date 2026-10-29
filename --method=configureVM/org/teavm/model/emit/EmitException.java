package org.teavm.model.emit;

public class EmitException extends RuntimeException {
   private static final long serialVersionUID = 7479098357353665286L;

   public EmitException() {
   }

   public EmitException(String message) {
      super(message);
   }
}
