package org.teavm.backend.wasm.debug;

import org.teavm.backend.wasm.debug.info.FieldType;
import org.teavm.model.PrimitiveType;

public interface DebugClassLayout {
   void startClass(String var1, int var2, int var3, int var4);

   void instanceField(String var1, int var2, FieldType var3);

   void staticField(String var1, int var2, FieldType var3);

   void endClass();

   void writeInterface(String var1, int var2);

   void writePrimitive(PrimitiveType var1, int var2);

   void writeArray(int var1, int var2);

   void writeUnknown(int var1);
}
