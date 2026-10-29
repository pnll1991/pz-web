package org.teavm.backend.wasm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.backend.wasm.model.WasmBlockType;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.render.WasmSignature;

public class WasmFunctionTypes {
   private WasmModule module;
   private Map<WasmSignature, WasmFunctionType> types = new HashMap<>();

   public WasmFunctionTypes(WasmModule module) {
      this.module = module;
   }

   public WasmFunctionType get(WasmSignature signature) {
      return this.types.computeIfAbsent(signature, k -> {
         WasmFunctionType type = new WasmFunctionType(null, signature.getReturnTypes(), signature.getParameterTypes());
         this.module.types.add(type);
         return type;
      });
   }

   public WasmFunctionType of(WasmType returnType, WasmType... parameterTypes) {
      return this.get(new WasmSignature(returnType != null ? List.of(returnType) : List.of(), List.of(parameterTypes)));
   }

   public WasmBlockType blockType(List<? extends WasmType> types) {
      if (types.isEmpty()) {
         return null;
      } else {
         return types.size() == 1 ? types.get(0).asBlock() : this.get(new WasmSignature(List.copyOf(types), List.of())).asBlock();
      }
   }
}
