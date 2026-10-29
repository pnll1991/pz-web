package org.teavm.backend.wasm.debug.info;

public abstract class FieldInfo {
   public abstract int address();

   public abstract String name();

   public abstract FieldType type();
}
