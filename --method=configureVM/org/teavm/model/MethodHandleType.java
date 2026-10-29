package org.teavm.model;

public enum MethodHandleType {
   GET_FIELD(1),
   GET_STATIC_FIELD(2),
   PUT_FIELD(3),
   PUT_STATIC_FIELD(4),
   INVOKE_VIRTUAL(5),
   INVOKE_STATIC(6),
   INVOKE_SPECIAL(7),
   INVOKE_CONSTRUCTOR(8),
   INVOKE_INTERFACE(9);

   private final int referenceKind;

   MethodHandleType(int referenceKind) {
      this.referenceKind = referenceKind;
   }

   public int getReferenceKind() {
      return this.referenceKind;
   }
}
