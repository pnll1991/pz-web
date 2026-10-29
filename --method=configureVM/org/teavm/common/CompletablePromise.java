package org.teavm.common;

public class CompletablePromise<T> extends Promise<T> {
   @Override
   public void complete(T value) {
      super.complete(value);
   }

   @Override
   public void completeWithError(Throwable e) {
      super.completeWithError(e);
   }
}
