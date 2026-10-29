package org.teavm.backend.wasm.debug.info;

public interface InterfaceLayout extends TypeLayout {
   ClassInfo classRef();

   @Override
   default TypeLayoutKind kind() {
      return TypeLayoutKind.INTERFACE;
   }
}
