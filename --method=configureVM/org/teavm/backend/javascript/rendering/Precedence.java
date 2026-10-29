package org.teavm.backend.javascript.rendering;

public enum Precedence {
   COMMA,
   ASSIGNMENT,
   CONDITIONAL,
   LOGICAL_OR,
   LOGICAL_AND,
   BITWISE_OR,
   BITWISE_XOR,
   BITWISE_AND,
   EQUALITY,
   COMPARISON,
   BITWISE_SHIFT,
   ADDITION,
   MODULO,
   MULTIPLICATION,
   UNARY,
   NEW,
   FUNCTION_CALL,
   MEMBER_ACCESS,
   GROUPING;

   private static Precedence[] cache = values();

   public Precedence next() {
      int index = this.ordinal();
      return index + 1 < cache.length ? cache[index + 1] : cache[index];
   }

   public Precedence previous() {
      int index = this.ordinal();
      return index > 0 ? cache[index - 1] : cache[index];
   }

   public static Precedence min() {
      return COMMA;
   }
}
