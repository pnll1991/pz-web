package org.teavm.backend.wasm.model;

public abstract class WasmType {
   public static final WasmType.Number INT32 = new WasmType.Number(WasmNumType.INT32);
   public static final WasmType.Number INT64 = new WasmType.Number(WasmNumType.INT64);
   public static final WasmType.Number FLOAT32 = new WasmType.Number(WasmNumType.FLOAT32);
   public static final WasmType.Number FLOAT64 = new WasmType.Number(WasmNumType.FLOAT64);
   public static final WasmType.SpecialReference FUNC = WasmType.SpecialReferenceKind.FUNC.asType();
   public static final WasmType.SpecialReference ANY = WasmType.SpecialReferenceKind.ANY.asType();
   public static final WasmType.SpecialReference EQ = WasmType.SpecialReferenceKind.EQ.asType();
   public static final WasmType.SpecialReference EXTERN = WasmType.SpecialReferenceKind.EXTERN.asType();
   public static final WasmType.SpecialReference STRUCT = WasmType.SpecialReferenceKind.STRUCT.asType();
   public static final WasmType.SpecialReference ARRAY = WasmType.SpecialReferenceKind.ARRAY.asType();
   public static final WasmType.SpecialReference EXN = WasmType.SpecialReferenceKind.EXN.asType();
   public static final WasmType.SpecialReference I31 = WasmType.SpecialReferenceKind.I31.asType();
   private WasmStorageType.Regular storageType;
   private WasmBlockType.Value blockType;

   private WasmType() {
   }

   public WasmStorageType.Regular asStorage() {
      if (this.storageType == null) {
         this.storageType = new WasmStorageType.Regular(this);
      }

      return this.storageType;
   }

   public WasmBlockType.Value asBlock() {
      if (this.blockType == null) {
         this.blockType = new WasmBlockType.Value(this);
      }

      return this.blockType;
   }

   public static WasmType.Number num(WasmNumType number) {
      switch (number) {
         case INT32:
            return INT32;
         case INT64:
            return INT64;
         case FLOAT32:
            return FLOAT32;
         case FLOAT64:
            return FLOAT64;
         default:
            throw new IllegalArgumentException();
      }
   }

   public static final class CompositeReference extends WasmType.Reference {
      public final WasmCompositeType composite;

      CompositeReference(WasmCompositeType composite, boolean nullable) {
         super(nullable);
         this.composite = composite;
      }

      @Override
      public WasmType.Reference asNonNull() {
         return this.composite.getNonNullReference();
      }

      @Override
      public WasmType.Reference asNullable() {
         return this.composite.getReference();
      }
   }

   public static final class Number extends WasmType {
      public final WasmNumType number;

      private Number(WasmNumType number) {
         this.number = number;
      }
   }

   public abstract static class Reference extends WasmType {
      private final boolean nullable;

      Reference(boolean nullable) {
         this.nullable = nullable;
      }

      public boolean isNullable() {
         return this.nullable;
      }

      public abstract WasmType.Reference asNullable();

      public abstract WasmType.Reference asNonNull();
   }

   public static final class SpecialReference extends WasmType.Reference {
      public final WasmType.SpecialReferenceKind kind;

      private SpecialReference(WasmType.SpecialReferenceKind kind, boolean nullable) {
         super(nullable);
         this.kind = kind;
      }

      @Override
      public WasmType.Reference asNonNull() {
         return this.kind.nonNullType;
      }

      @Override
      public WasmType.Reference asNullable() {
         return this.kind.type;
      }
   }

   public enum SpecialReferenceKind {
      FUNC,
      ANY,
      EQ,
      EXTERN,
      STRUCT,
      ARRAY,
      EXN,
      I31;

      private WasmType.SpecialReference type;
      private WasmType.SpecialReference nonNullType;

      public final WasmType.SpecialReference asType() {
         if (this.type == null) {
            this.type = new WasmType.SpecialReference(this, true);
         }

         return this.type;
      }

      public final WasmType.SpecialReference asNonNullType() {
         if (this.nonNullType == null) {
            this.nonNullType = new WasmType.SpecialReference(this, false);
         }

         return this.nonNullType;
      }
   }
}
