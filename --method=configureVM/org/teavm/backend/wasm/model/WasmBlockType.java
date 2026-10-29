package org.teavm.backend.wasm.model;

import java.util.List;

public abstract class WasmBlockType {
   private WasmBlockType() {
   }

   public abstract List<? extends WasmType> getInputTypes();

   public abstract List<? extends WasmType> getOutputTypes();

   public static class Function extends WasmBlockType {
      public final WasmFunctionType ref;

      Function(WasmFunctionType ref) {
         this.ref = ref;
      }

      @Override
      public List<? extends WasmType> getInputTypes() {
         return this.ref.getParameterTypes();
      }

      @Override
      public List<? extends WasmType> getOutputTypes() {
         return this.ref.getReturnTypes();
      }
   }

   public static class Value extends WasmBlockType {
      public final WasmType type;

      Value(WasmType type) {
         this.type = type;
      }

      @Override
      public List<? extends WasmType> getInputTypes() {
         return List.of();
      }

      @Override
      public List<? extends WasmType> getOutputTypes() {
         return this.type != null ? List.of(this.type) : List.of();
      }
   }
}
