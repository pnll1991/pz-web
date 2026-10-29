package org.teavm.backend.wasm.generate.strings;

public interface WasmGCStringProvider {
   WasmGCStringConstant getStringConstant(String var1);
}
