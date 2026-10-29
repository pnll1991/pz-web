package org.teavm.ast;

import org.teavm.model.util.VariableType;

public class VariableNode {
   private int index;
   private int originalIndex;
   private VariableType type;
   private String name;

   public VariableNode(int index, VariableType type) {
      this.index = index;
      this.originalIndex = index;
      this.type = type;
   }

   public int getOriginalIndex() {
      return this.originalIndex;
   }

   public int getIndex() {
      return this.index;
   }

   public void setIndex(int index) {
      this.index = index;
   }

   public VariableType getType() {
      return this.type;
   }

   public void setType(VariableType type) {
      this.type = type;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }
}
