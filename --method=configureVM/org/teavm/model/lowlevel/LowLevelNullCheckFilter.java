package org.teavm.model.lowlevel;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.transformation.NullCheckFilter;

public class LowLevelNullCheckFilter implements NullCheckFilter {
   private Characteristics characteristics;

   public LowLevelNullCheckFilter(Characteristics characteristics) {
      this.characteristics = characteristics;
   }

   @Override
   public boolean apply(FieldReference field) {
      return !this.characteristics.isStructure(field.getClassName())
         && !this.characteristics.isReflectionSpecial(field.getClassName())
         && !this.characteristics.isResource(field.getClassName());
   }

   @Override
   public boolean apply(MethodReference method) {
      return this.characteristics.isManaged(method.getClassName())
         && this.characteristics.isManaged(method)
         && !this.characteristics.isResource(method.getClassName());
   }
}
