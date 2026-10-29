package org.teavm.backend.wasm.intrinsics;

import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCInitializerRegistry;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.vm.intrinsic.IntrinsicContributorContext;

public interface WasmGCCodeGenContext extends IntrinsicContributorContext {
   WasmModule module();

   WasmFunctionTypes functionTypes();

   BaseWasmFunctionRepository functions();

   WasmGCTypeMapper typeMapper();

   WasmGCClassInfoProvider classInfoProvider();

   WasmGCVirtualTableProvider virtualTables();

   WasmGCNameProvider names();

   WasmGCStringProvider strings();

   WasmTag exceptionTag();

   WasmGCInitializerRegistry initializerRegistry();
}
