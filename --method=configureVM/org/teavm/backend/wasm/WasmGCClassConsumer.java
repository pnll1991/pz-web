package org.teavm.backend.wasm;

import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenContext;

public interface WasmGCClassConsumer {
   void accept(WasmGCCodeGenContext var1, String var2);
}
