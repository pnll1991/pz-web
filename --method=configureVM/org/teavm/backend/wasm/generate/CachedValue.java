package org.teavm.backend.wasm.generate;

import org.teavm.backend.wasm.model.instruction.WasmFragmentBuilder;

public abstract class CachedValue implements WasmFragmentBuilder {
   public void release() {
   }
}
