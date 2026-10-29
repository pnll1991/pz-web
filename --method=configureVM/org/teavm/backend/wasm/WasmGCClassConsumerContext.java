package org.teavm.backend.wasm;

import java.util.function.Consumer;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.model.ClassReaderSource;

public interface WasmGCClassConsumerContext {
   ClassReaderSource classes();

   WasmModule module();

   WasmFunctionTypes functionTypes();

   BaseWasmFunctionRepository functions();

   WasmGCNameProvider names();

   WasmGCStringProvider strings();

   WasmGCTypeMapper typeMapper();

   WasmTag exceptionTag();

   String entryPoint();

   void addToInitializer(Consumer<WasmFunction> var1);
}
