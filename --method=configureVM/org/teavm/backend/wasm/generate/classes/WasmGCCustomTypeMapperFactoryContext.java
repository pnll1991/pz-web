package org.teavm.backend.wasm.generate.classes;

import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.model.ClassReaderSource;

public interface WasmGCCustomTypeMapperFactoryContext {
   ClassReaderSource classes();

   ClassReaderSource originalClasses();

   WasmModule module();

   WasmGCClassInfoProvider classInfoProvider();

   WasmGCTypeMapper typeMapper();

   WasmGCNameProvider names();

   WasmFunctionTypes functionTypes();
}
