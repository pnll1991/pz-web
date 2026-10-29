package org.teavm.backend.wasm.render;

import java.util.List;
import java.util.Objects;
import org.teavm.backend.wasm.model.WasmType;

public final class WasmSignature {
   private List<? extends WasmType> returnTypes;
   private List<? extends WasmType> types;

   public WasmSignature(List<? extends WasmType> returnType, List<? extends WasmType> parameterTypes) {
      this.returnTypes = Objects.requireNonNull(returnType);
      this.types = Objects.requireNonNull(parameterTypes);
   }

   public List<? extends WasmType> getReturnTypes() {
      return this.returnTypes;
   }

   public List<? extends WasmType> getParameterTypes() {
      return this.types;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         WasmSignature that = (WasmSignature)o;
         return Objects.equals(this.types, that.types) && Objects.equals(this.returnTypes, that.returnTypes);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.types, this.returnTypes);
   }
}
