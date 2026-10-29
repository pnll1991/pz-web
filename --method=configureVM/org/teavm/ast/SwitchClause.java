package org.teavm.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SwitchClause {
   private int[] conditions;
   private List<Statement> body = new ArrayList<>();

   public int[] getConditions() {
      return this.conditions != null ? Arrays.copyOf(this.conditions, this.conditions.length) : null;
   }

   public void setConditions(int[] conditions) {
      this.conditions = conditions != null ? Arrays.copyOf(conditions, conditions.length) : null;
   }

   public List<Statement> getBody() {
      return this.body;
   }
}
