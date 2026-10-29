package org.teavm.backend.wasm.intrinsics;

import org.teavm.vm.intrinsic.IntrinsicRegistry;

public interface WasmGCCodeGenRegistry {
   IntrinsicRegistry<WasmGCInlineIntrinsic> inlineIntrinsics();

   IntrinsicRegistry<WasmGCBodyIntrinsic> bodyIntrinsics();
}
