package org.teavm.backend.wasm;

import java.util.Collection;
import java.util.function.Supplier;
import org.teavm.backend.wasm.generate.classes.WasmGCCustomTypeMapperFactory;
import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenContributor;
import org.teavm.model.MethodReference;
import org.teavm.vm.spi.TeaVMHostExtension;

public interface TeaVMWasmGCHost extends TeaVMHostExtension {
   void addCustomTypeMapperFactory(WasmGCCustomTypeMapperFactory var1);

   void addMethodsOnCallSites(Supplier<Collection<MethodReference>> var1);

   void contributeToCodeGen(WasmGCCodeGenContributor var1);
}
