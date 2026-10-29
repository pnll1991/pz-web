package org.teavm.backend.wasm.types;

import java.util.Objects;
import org.teavm.model.ValueType;

public class PreciseValueType {
   public final ValueType valueType;
   public final boolean isArrayUnwrap;

   public PreciseValueType(ValueType valueType, boolean isArrayUnwrap) {
      this.valueType = valueType;
      this.isArrayUnwrap = isArrayUnwrap;
   }

   @Override
   public final boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof PreciseValueType that) ? false : this.isArrayUnwrap == that.isArrayUnwrap && this.valueType.equals(that.valueType);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.valueType, this.isArrayUnwrap);
   }
}
