package org.teavm.debugging;

public class Variable {
   private String name;
   private Value value;

   Variable(String name, Value value) {
      this.name = name;
      this.value = value;
   }

   public String getName() {
      return this.name;
   }

   public Value getValue() {
      return this.value;
   }
}
