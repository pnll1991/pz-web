package org.teavm.backend.wasm.parser;

import java.nio.charset.StandardCharsets;
import org.teavm.backend.wasm.model.WasmType;

public class WasmBinaryReader {
   private final AddressListener addressListener;
   public final byte[] data;
   public int ptr;
   private int lastReportedPtr = -1;

   public WasmBinaryReader(AddressListener addressListener, byte[] data) {
      this.addressListener = addressListener;
      this.data = data;
   }

   public void reportAddress() {
      if (this.ptr != this.lastReportedPtr) {
         this.lastReportedPtr = this.ptr;
         if (this.addressListener != null) {
            this.addressListener.address(this.ptr);
         }
      }
   }

   public WasmHollowStorageType readStorageType() {
      byte typeId = this.data[this.ptr];
      switch (typeId) {
         case 119:
            this.ptr++;
            return WasmHollowStorageType.INT16;
         case 120:
            this.ptr++;
            return WasmHollowStorageType.INT8;
         default:
            return new WasmHollowStorageType.Regular(this.readType());
      }
   }

   public WasmHollowBlockType readBlockType() {
      if (this.data[this.ptr] < 64) {
         return new WasmHollowBlockType.Function(this.readSignedLEB());
      }

      WasmHollowType result = this.readType();
      return result != null ? new WasmHollowBlockType.Value(result) : null;
   }

   public WasmHollowType readType() {
      byte typeId = this.data[this.ptr++];
      switch (typeId) {
         case 64:
            return null;
         case 99:
            return this.readHeapType(true);
         case 100:
            return this.readHeapType(false);
         case 124:
            return WasmHollowType.FLOAT64;
         case 125:
            return WasmHollowType.FLOAT32;
         case 126:
            return WasmHollowType.INT64;
         case 127:
            return WasmHollowType.INT32;
         default:
            return this.readAbsHeapType(typeId, true);
      }
   }

   public WasmHollowType.Reference readHeapType(boolean nullable) {
      byte typeId = this.data[this.ptr];
      if ((typeId & 192) == 64) {
         WasmHollowType.SpecialReference result = this.readAbsHeapType(typeId, nullable);
         this.ptr++;
         return result;
      } else {
         return new WasmHollowType.CompositeReference(this.readLEB(), nullable);
      }
   }

   public WasmHollowType.SpecialReference readAbsHeapType(int typeId, boolean nullable) {
      switch (typeId) {
         case 105:
            return special(WasmType.SpecialReferenceKind.EXN, nullable);
         case 106:
            return special(WasmType.SpecialReferenceKind.ARRAY, nullable);
         case 107:
            return special(WasmType.SpecialReferenceKind.STRUCT, nullable);
         case 108:
            return special(WasmType.SpecialReferenceKind.I31, nullable);
         case 109:
            return special(WasmType.SpecialReferenceKind.EQ, nullable);
         case 110:
            return special(WasmType.SpecialReferenceKind.ANY, nullable);
         case 111:
            return special(WasmType.SpecialReferenceKind.EXTERN, nullable);
         case 112:
            return special(WasmType.SpecialReferenceKind.FUNC, nullable);
         default:
            throw new ParseException("Unknown type", this.ptr);
      }
   }

   private static WasmHollowType.SpecialReference special(WasmType.SpecialReferenceKind kind, boolean nullable) {
      return nullable ? WasmHollowType.Reference.special(kind) : WasmHollowType.SpecialReference.nonNullSpecial(kind);
   }

   public int readSignedLEB() {
      int result = 0;
      int shift = 0;

      while (true) {
         byte digit = this.data[this.ptr++];
         result |= (digit & 127) << shift;
         if ((digit & 128) == 0) {
            if ((digit & 64) != 0) {
               result |= -1 << shift + 7;
            }

            return result;
         }

         shift += 7;
      }
   }

   public int readLEB() {
      int result = 0;
      int shift = 0;

      while (true) {
         byte digit = this.data[this.ptr++];
         result |= (digit & 127) << shift;
         if ((digit & 128) == 0) {
            return result;
         }

         shift += 7;
      }
   }

   public long readSignedLongLEB() {
      long result = 0L;
      int shift = 0;

      while (true) {
         byte digit = this.data[this.ptr++];
         result |= (digit & 127L) << shift;
         if ((digit & 128) == 0) {
            if ((digit & 64) != 0) {
               result |= -1L << shift + 7;
            }

            return result;
         }

         shift += 7;
      }
   }

   public long readLongLEB() {
      long result = 0L;
      int shift = 0;

      while (true) {
         byte digit = this.data[this.ptr++];
         result |= (digit & 127L) << shift;
         if ((digit & 128) == 0) {
            return result;
         }

         shift += 7;
      }
   }

   public int readInt32() {
      return this.data[this.ptr++] & 0xFF | (this.data[this.ptr++] & 0xFF) << 8 | (this.data[this.ptr++] & 0xFF) << 16 | (this.data[this.ptr++] & 0xFF) << 24;
   }

   public int readFixedInt() {
      return (this.data[this.ptr++] & 0xFF) << 24 | (this.data[this.ptr++] & 0xFF) << 16 | (this.data[this.ptr++] & 0xFF) << 8 | this.data[this.ptr++] & 0xFF;
   }

   public long readFixedLong() {
      return (this.data[this.ptr++] & 255L) << 56
         | (this.data[this.ptr++] & 255L) << 48
         | (this.data[this.ptr++] & 255L) << 40
         | (this.data[this.ptr++] & 255L) << 32
         | (this.data[this.ptr++] & 255L) << 24
         | (this.data[this.ptr++] & 255) << 16
         | (this.data[this.ptr++] & 255) << 8
         | this.data[this.ptr++] & 0xFF;
   }

   public String readString() {
      int size = this.readLEB();
      String result = new String(this.data, this.ptr, size, StandardCharsets.UTF_8);
      this.ptr += size;
      return result;
   }
}
