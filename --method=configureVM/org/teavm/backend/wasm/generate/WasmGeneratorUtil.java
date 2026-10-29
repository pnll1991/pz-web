package org.teavm.backend.wasm.generate;

import org.teavm.ast.OperationType;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.model.ValueType;
import org.teavm.model.util.VariableType;

public final class WasmGeneratorUtil {
   public static final int PAGE_SIZE = 65536;

   private WasmGeneratorUtil() {
   }

   public static WasmNumType mapType(OperationType type) {
      switch (type) {
         case INT:
            return WasmNumType.INT32;
         case LONG:
            return WasmNumType.INT64;
         case FLOAT:
            return WasmNumType.FLOAT32;
         case DOUBLE:
            return WasmNumType.FLOAT64;
         default:
            throw new IllegalArgumentException(type.toString());
      }
   }

   public static WasmType mapType(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case CHARACTER:
            case INTEGER:
               return WasmType.INT32;
            case LONG:
               return WasmType.INT64;
            case FLOAT:
               return WasmType.FLOAT32;
            case DOUBLE:
               return WasmType.FLOAT64;
         }
      } else if (type == ValueType.VOID) {
         return null;
      }

      return WasmType.INT32;
   }

   public static WasmType mapType(VariableType type) {
      switch (type) {
         case INT:
            return WasmType.INT32;
         case LONG:
            return WasmType.INT64;
         case FLOAT:
            return WasmType.FLOAT32;
         case DOUBLE:
            return WasmType.FLOAT64;
         default:
            return WasmType.INT32;
      }
   }

   public static int getNativeTypeSize(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
               return 1;
            case SHORT:
            case CHARACTER:
               return 2;
            case INTEGER:
            case FLOAT:
               return 4;
            case LONG:
            case DOUBLE:
               return 8;
         }
      }

      return 4;
   }

   public static int align(int base, int alignment) {
      return base == 0 ? 0 : ((base - 1) / alignment + 1) * alignment;
   }
}
