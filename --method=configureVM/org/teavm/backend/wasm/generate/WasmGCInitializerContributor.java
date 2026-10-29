package org.teavm.backend.wasm.generate;

import org.teavm.backend.wasm.model.WasmFunction;

public interface WasmGCInitializerContributor {
   void contributeToInitializerDefinitions(WasmFunction var1);

   void contributeToInitializer(WasmFunction var1);
}
