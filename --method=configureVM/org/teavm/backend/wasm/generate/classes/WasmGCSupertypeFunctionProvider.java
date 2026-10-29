package org.teavm.backend.wasm.generate.classes;

import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.model.ValueType;

public interface WasmGCSupertypeFunctionProvider {
   WasmFunction getIsSupertypeFunction(ValueType var1);

   WasmFunctionType getFunctionType();
}
