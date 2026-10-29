package org.teavm.backend.wasm;

import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.model.MethodReference;

public interface BaseWasmFunctionRepository {
   WasmFunction forStaticMethod(MethodReference var1);

   WasmFunction forInstanceMethod(MethodReference var1);
}
