package org.teavm.backend.wasm.types;

import org.teavm.model.ClassHierarchy;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.util.VariableCategoryProvider;

public class WasmGCVariableCategoryProvider implements VariableCategoryProvider {
   private ClassHierarchy hierarchy;
   private boolean compactMode;

   public WasmGCVariableCategoryProvider(ClassHierarchy hierarchy) {
      this.hierarchy = hierarchy;
   }

   public void setCompactMode(boolean compactMode) {
      this.compactMode = compactMode;
   }

   @Override
   public Object[] getCategories(Program program, MethodReference method) {
      PreciseTypeInference inference = new PreciseTypeInference(program, method, this.hierarchy);
      inference.setPhisSkipped(false);
      Object[] result = new Object[program.variableCount()];

      for (int i = 0; i < program.variableCount(); i++) {
         PreciseValueType type = inference.typeOf(program.variableAt(i));
         result[i] = type != null ? type : new Object();
      }

      if (this.compactMode) {
         result[0] = ValueType.object(method.getClassName());
      }

      return result;
   }
}
