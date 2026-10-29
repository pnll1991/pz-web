package org.teavm.backend.wasm.parser;

import org.teavm.backend.wasm.model.WasmPackedType;

public abstract class WasmHollowStorageType {
   public static final WasmHollowStorageType.Packed INT16 = new WasmHollowStorageType.Packed(WasmPackedType.INT16);
   public static final WasmHollowStorageType.Packed INT8 = new WasmHollowStorageType.Packed(WasmPackedType.INT8);

   private WasmHollowStorageType() {
   }

   public abstract WasmHollowType asUnpackedType();

   public static WasmHollowStorageType.Packed packed(WasmPackedType type) {
      switch (type) {
         case INT8:
            return INT8;
         case INT16:
            return INT16;
         default:
            throw new IllegalArgumentException();
      }
   }

   public static final class Packed extends WasmHollowStorageType {
      public final WasmPackedType type;

      private Packed(WasmPackedType type) {
         this.type = type;
      }

      @Override
      public WasmHollowType asUnpackedType() {
         return WasmHollowType.INT32;
      }
   }

   public static final class Regular extends WasmHollowStorageType {
      public final WasmHollowType type;

      Regular(WasmHollowType type) {
         this.type = type;
      }

      @Override
      public WasmHollowType asUnpackedType() {
         return this.type;
      }
   }
}
