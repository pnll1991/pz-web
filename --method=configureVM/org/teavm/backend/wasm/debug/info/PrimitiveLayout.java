package org.teavm.backend.wasm.debug.info;

import org.teavm.model.PrimitiveType;

public interface PrimitiveLayout extends TypeLayout {
   PrimitiveType primitiveType();

   @Override
   default TypeLayoutKind kind() {
      return TypeLayoutKind.PRIMITIVE;
   }
}
