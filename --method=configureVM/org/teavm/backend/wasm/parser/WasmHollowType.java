package org.teavm.backend.wasm.parser;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmType;

public class WasmHollowType {
   public static final WasmHollowType.Number INT32 = new WasmHollowType.Number(WasmNumType.INT32);
   public static final WasmHollowType.Number INT64 = new WasmHollowType.Number(WasmNumType.INT64);
   public static final WasmHollowType.Number FLOAT32 = new WasmHollowType.Number(WasmNumType.FLOAT32);
   public static final WasmHollowType.Number FLOAT64 = new WasmHollowType.Number(WasmNumType.FLOAT64);

   private WasmHollowType() {
   }

   public static WasmHollowType.Number num(WasmNumType number) {
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

   public static final class CompositeReference extends WasmHollowType.Reference {
      public final int index;

      public CompositeReference(int index, boolean nullable) {
         super(nullable);
         this.index = index;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof WasmHollowType.CompositeReference that) ? false : this.index == that.index && this.isNullable() == that.isNullable();
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.index, this.isNullable());
      }
   }

   public static final class Number extends WasmHollowType {
      public final WasmNumType number;

      private Number(WasmNumType number) {
         this.number = number;
      }
   }

   public abstract static class Reference extends WasmHollowType {
      private static final WasmHollowType.SpecialReference[] references = new WasmHollowType.SpecialReference[WasmType.SpecialReferenceKind.values().length];
      private static final WasmHollowType.SpecialReference[] nonNullReferences = new WasmHollowType.SpecialReference[WasmType.SpecialReferenceKind.values().length];
      private final boolean nullable;

      public Reference(boolean nullable) {
         this.nullable = nullable;
      }

      public boolean isNullable() {
         return this.nullable;
      }

      public static WasmHollowType.SpecialReference special(WasmType.SpecialReferenceKind kind) {
         WasmHollowType.SpecialReference result = references[kind.ordinal()];
         if (result == null) {
            result = new WasmHollowType.SpecialReference(kind, true);
            references[kind.ordinal()] = result;
         }

         return result;
      }

      public static WasmHollowType.SpecialReference nonNullSpecial(WasmType.SpecialReferenceKind kind) {
         WasmHollowType.SpecialReference result = nonNullReferences[kind.ordinal()];
         if (result == null) {
            result = new WasmHollowType.SpecialReference(kind, false);
            nonNullReferences[kind.ordinal()] = result;
         }

         return result;
      }
   }

   public static final class SpecialReference extends WasmHollowType.Reference {
      public final WasmType.SpecialReferenceKind kind;

      SpecialReference(WasmType.SpecialReferenceKind kind, boolean nullable) {
         super(nullable);
         this.kind = kind;
      }
   }
}
