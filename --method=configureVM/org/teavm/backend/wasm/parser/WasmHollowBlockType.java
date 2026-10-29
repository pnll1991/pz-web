package org.teavm.backend.wasm.parser;

public class WasmHollowBlockType {
   private WasmHollowBlockType() {
   }

   public static class Function extends WasmHollowBlockType {
      public final int ref;

      public Function(int ref) {
         this.ref = ref;
      }
   }

   public static class Value extends WasmHollowBlockType {
      public final WasmHollowType type;

      public Value(WasmHollowType type) {
         this.type = type;
      }
   }
}
