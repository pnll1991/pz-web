package org.teavm.backend.javascript.rendering;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.ast.MethodNode;
import org.teavm.ast.VariableNode;

public class VariableNameGenerator {
   private boolean minifying;
   private final List<String> cachedVariableNames = new ArrayList<>();
   private int cachedVariableNameLastIndex;
   private MethodNode currentMethod;
   private Set<String> usedVariableNames = new HashSet<>();

   public VariableNameGenerator(boolean minifying) {
      this.minifying = minifying;
   }

   public void setCurrentMethod(MethodNode currentMethod) {
      this.currentMethod = currentMethod;
   }

   public void clear() {
      this.cachedVariableNames.clear();
      this.usedVariableNames.clear();
      this.cachedVariableNameLastIndex = 0;
      this.init();
   }

   private void init() {
      if (!this.minifying) {
         this.usedVariableNames.add("$tmp");
         this.usedVariableNames.add("$ptr");
         this.usedVariableNames.add("$thread");
      }
   }

   public String variableName(int index) {
      if (this.minifying) {
         return this.minifiedVariableName(index);
      }

      while (index >= this.cachedVariableNames.size()) {
         this.cachedVariableNames.add(null);
      }

      String name = this.cachedVariableNames.get(index);
      if (name == null) {
         name = this.generateVariableName(index);
         this.cachedVariableNames.set(index, name);
      }

      return name;
   }

   public String minifiedVariableName(int index) {
      while (index >= this.cachedVariableNames.size()) {
         String name;
         do {
            name = RenderingUtil.indexToId(this.cachedVariableNameLastIndex++);
         } while (RenderingUtil.KEYWORDS.contains(name));

         this.cachedVariableNames.add(name);
      }

      return this.cachedVariableNames.get(index);
   }

   private String generateVariableName(int index) {
      VariableNode variable = this.currentMethod != null && index < this.currentMethod.getVariables().size()
         ? this.currentMethod.getVariables().get(index)
         : null;
      if (variable != null && variable.getName() != null) {
         String result = "$" + RenderingUtil.escapeName(variable.getName());
         if (RenderingUtil.KEYWORDS.contains(result) || !this.usedVariableNames.add(result)) {
            String base = result;
            int suffix = 0;

            do {
               result = base + "_" + suffix++;
            } while (!this.usedVariableNames.add(result));
         }

         return result;
      } else {
         return "var$" + index;
      }
   }
}
