package org.teavm.backend.wasm.generate;

import java.util.function.Consumer;
import org.teavm.backend.wasm.model.WasmFunction;

public interface WasmGCInitializerRegistry {
   void register(Consumer<WasmFunction> var1);
}
