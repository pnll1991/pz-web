package org.teavm.backend.wasm.debug.info;

public interface ArrayLayout extends TypeLayout {
   TypeLayout elementType();

   @Override
   default TypeLayoutKind kind() {
      return TypeLayoutKind.ARRAY;
   }
}
