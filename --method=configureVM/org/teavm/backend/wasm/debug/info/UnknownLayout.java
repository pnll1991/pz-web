package org.teavm.backend.wasm.debug.info;

public interface UnknownLayout extends TypeLayout {
   @Override
   default TypeLayoutKind kind() {
      return TypeLayoutKind.UNKNOWN;
   }
}
