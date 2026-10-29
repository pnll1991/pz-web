package org.teavm.backend.wasm.debug.info;

public interface TypeLayout {
   TypeLayoutKind kind();

   int address();
}
