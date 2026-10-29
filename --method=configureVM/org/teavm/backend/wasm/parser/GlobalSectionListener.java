package org.teavm.backend.wasm.parser;

public interface GlobalSectionListener {
   CodeListener startGlobal(int var1, WasmHollowType var2, boolean var3);

   void endGlobal();
}
