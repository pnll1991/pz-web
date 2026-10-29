package org.teavm.backend.wasm.intrinsics;

import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.model.MethodReference;

public interface WasmGCBodyIntrinsic {
   void apply(MethodReference var1, WasmFunction var2);
}
