package org.teavm.backend.wasm.generate.methods;

import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.model.ClassReaderSource;
import org.teavm.parsing.resource.ResourceProvider;

public interface BaseWasmGenerationContext {
   BaseWasmFunctionRepository functions();

   WasmFunctionTypes functionTypes();

   WasmTag getExceptionTag();

   ClassReaderSource classes();

   ResourceProvider resources();
}
