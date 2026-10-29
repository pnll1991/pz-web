package org.teavm.model.util;

import org.teavm.model.MethodReference;
import org.teavm.model.Program;

public class DefaultVariableCategoryProvider implements VariableCategoryProvider {
   @Override
   public Object[] getCategories(Program program, MethodReference method) {
      TypeInferer inferer = new TypeInferer();
      inferer.inferTypes(program, method);
      Object[] categories = new Object[program.variableCount()];

      for (int i = 0; i < program.variableCount(); i++) {
         categories[i] = this.getCategory(inferer.typeOf(i));
      }

      return categories;
   }

   private int getCategory(VariableType type) {
      if (type == null) {
         return 255;
      }

      switch (type) {
         case INT:
            return 0;
         case LONG:
            return 1;
         case FLOAT:
            return 2;
         case DOUBLE:
            return 3;
         case OBJECT:
            return 4;
         default:
            return 5;
      }
   }
}
