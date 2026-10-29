package org.teavm.backend.wasm.intrinsics;

import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.backend.wasm.model.instruction.WasmInt32Subtype;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.model.MethodReference;

public class WasmGCStringPoolIntrinsic implements WasmGCBodyIntrinsic {
   private WasmModule module;
   private WasmGCNameProvider names;

   public WasmGCStringPoolIntrinsic(WasmModule module, WasmGCNameProvider names) {
      this.module = module;
      this.names = names;
   }

   @Override
   public void apply(MethodReference method, WasmFunction function) {
      WasmGlobal pointer = new WasmGlobal(this.names.topLevel("teavm@stringPoolPointer"), WasmType.INT32);
      pointer.getInitialValue().add(new WasmInt32Constant(0));
      this.module.globals.add(pointer);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getGlobal(pointer).loadI32(1, 0, WasmInt32Subtype.UINT8);
      body.getGlobal(pointer).i32Const(1).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD).setGlobal(pointer);
   }
}
