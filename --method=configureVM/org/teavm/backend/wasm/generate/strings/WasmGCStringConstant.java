package org.teavm.backend.wasm.generate.strings;

import org.teavm.backend.wasm.model.WasmGlobal;

public class WasmGCStringConstant {
   public final int index;
   public final WasmGlobal global;

   public WasmGCStringConstant(int index, WasmGlobal global) {
      this.index = index;
      this.global = global;
   }
}
