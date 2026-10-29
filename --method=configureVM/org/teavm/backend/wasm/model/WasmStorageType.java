package org.teavm.backend.wasm.model;

public abstract class WasmStorageType {
   public static final WasmStorageType.Packed INT16 = new WasmStorageType.Packed(WasmPackedType.INT16);
   public static final WasmStorageType.Packed INT8 = new WasmStorageType.Packed(WasmPackedType.INT8);

   private WasmStorageType() {
   }

   public abstract WasmType asUnpackedType();

   public static WasmStorageType.Packed packed(WasmPackedType type) {
      switch (type) {
         case INT8:
            return INT8;
         case INT16:
            return INT16;
         default:
            throw new IllegalArgumentException();
      }
   }

   public static final class Packed extends WasmStorageType {
      public final WasmPackedType type;

      private Packed(WasmPackedType type) {
         this.type = type;
      }

      @Override
      public WasmType asUnpackedType() {
         return WasmType.INT32;
      }
   }

   public static final class Regular extends WasmStorageType {
      public final WasmType type;

      Regular(WasmType type) {
         this.type = type;
      }

      @Override
      public WasmType asUnpackedType() {
         return this.type;
      }
   }
}
