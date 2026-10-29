package org.teavm.backend.wasm.intrinsics;

import org.teavm.backend.wasm.generate.classes.WasmGCCustomTypeMapper;
import org.teavm.backend.wasm.generate.classes.WasmGCCustomTypeMapperFactory;
import org.teavm.backend.wasm.generate.classes.WasmGCCustomTypeMapperFactoryContext;
import org.teavm.backend.wasm.model.WasmType;

public class WasmGCAsyncTypeMapperFactory implements WasmGCCustomTypeMapperFactory {
   @Override
   public WasmGCCustomTypeMapper createTypeMapper(WasmGCCustomTypeMapperFactoryContext context) {
      return className -> {
         if (className.equals("org.teavm.runtime.Fiber$PlatformObject")) {
            return WasmType.ANY;
         } else if (className.equals("org.teavm.runtime.Fiber$PlatformFunction")) {
            return WasmType.FUNC;
         } else {
            return className.equals("org.teavm.runtime.Fiber$PlatformException") ? WasmType.EXN : null;
         }
      };
   }
}
