package org.teavm.backend.wasm.debug.info;

import java.util.Collection;

public interface ClassLayout extends TypeLayout {
   ClassInfo classRef();

   ClassLayout superclass();

   Collection<? extends FieldInfo> instanceFields();

   Collection<? extends FieldInfo> staticFields();

   int size();

   @Override
   default TypeLayoutKind kind() {
      return TypeLayoutKind.CLASS;
   }
}
