package org.teavm.backend.wasm.parser;

public class WasmHollowFunctionType {
   public final WasmHollowType[] parameterTypes;
   public final WasmHollowType[] returnTypes;

   public WasmHollowFunctionType(WasmHollowType[] parameterTypes, WasmHollowType[] returnTypes) {
      this.parameterTypes = parameterTypes;
      this.returnTypes = returnTypes;
   }
}
