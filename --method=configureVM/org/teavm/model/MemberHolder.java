package org.teavm.model;

public abstract class MemberHolder extends ElementHolder implements MemberReader {
   public MemberHolder(String name) {
      super(name);
   }

   @Override
   public abstract String getOwnerName();
}
