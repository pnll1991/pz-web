package org.teavm.backend.wasm.render;

import java.util.Arrays;
import org.teavm.backend.wasm.model.WasmCompositeType;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmType;

public class WasmBinaryWriter {
   private byte[] data = new byte[1024];
   private int pointer;

   public void writeByte(int v) {
      this.alloc(1);
      this.data[this.pointer++] = (byte)v;
   }

   public void writeType(WasmType type, WasmModule module) {
      if (type == null) {
         this.writeByte(64);
      } else {
         if (type instanceof WasmType.Number) {
            this.writeType(((WasmType.Number)type).number);
         } else if (type instanceof WasmType.SpecialReference refType) {
            if (!refType.isNullable()) {
               this.writeByte(100);
            }

            this.writeSpecialHeapType(refType.kind);
         } else if (type instanceof WasmType.CompositeReference refType) {
            this.writeByte(refType.isNullable() ? 99 : 100);
            int index = module.types.indexOf(refType.composite);
            this.writeSignedLEB(index);
         }
      }
   }

   public void writeHeapType(WasmType.Reference type, WasmModule module) {
      if (type instanceof WasmType.CompositeReference) {
         WasmCompositeType composite = ((WasmType.CompositeReference)type).composite;
         int index = module.types.indexOf(composite);
         this.writeSignedLEB(index);
      } else {
         this.writeSpecialHeapType(((WasmType.SpecialReference)type).kind);
      }
   }

   private void writeSpecialHeapType(WasmType.SpecialReferenceKind type) {
      switch (type) {
         case ANY:
            this.writeByte(110);
            break;
         case EQ:
            this.writeByte(109);
            break;
         case EXTERN:
            this.writeByte(111);
            break;
         case FUNC:
            this.writeByte(112);
            break;
         case STRUCT:
            this.writeByte(107);
            break;
         case EXN:
            this.writeByte(105);
            break;
         case ARRAY:
            this.writeByte(106);
            break;
         case I31:
            this.writeByte(108);
      }
   }

   public void writeType(WasmNumType type) {
      switch (type) {
         case INT32:
            this.writeByte(127);
            break;
         case INT64:
            this.writeByte(126);
            break;
         case FLOAT32:
            this.writeByte(125);
            break;
         case FLOAT64:
            this.writeByte(124);
      }
   }

   public int getPosition() {
      return this.pointer;
   }

   public void writeBytes(byte[] bytes) {
      this.alloc(bytes.length);
      System.arraycopy(bytes, 0, this.data, this.pointer, bytes.length);
      this.pointer += bytes.length;
   }

   public void writeAsciiString(String str) {
      this.writeLEB(str.length());
      byte[] bytes = new byte[str.length()];

      for (int i = 0; i < str.length(); i++) {
         bytes[i] = (byte)str.charAt(i);
      }

      this.writeBytes(bytes);
   }

   public void writeInt32(int v) {
      this.alloc(4);

      for (int i = 0; i < 4; i++) {
         this.data[this.pointer++] = (byte)(v & 0xFF);
         v >>>= 8;
      }
   }

   public void writeLEB(int v) {
      this.alloc(5);

      while (true) {
         int digit = v & 127;
         int next = v >>> 7;
         boolean last = next == 0;
         if (!last) {
            digit |= -128;
         }

         this.data[this.pointer++] = (byte)digit;
         if (last) {
            return;
         }

         v = next;
      }
   }

   public void writeLEB4(int v) {
      this.alloc(4);

      for (int i = 0; i < 3; i++) {
         int digit = v & 127;
         this.data[this.pointer++] = (byte)(digit | 128);
         v >>>= 7;
      }

      this.data[this.pointer++] = (byte)(v & 127);
   }

   public void writeSignedLEB(int v) {
      this.alloc(5);

      boolean last;
      do {
         int digit = v & 127;
         v >>= 7;
         last = v == 0 && (digit & 64) == 0 || v == -1 && (digit & 64) != 0;
         if (!last) {
            digit |= -128;
         }

         this.data[this.pointer++] = (byte)digit;
      } while (!last);
   }

   public void writeLEB(long v) {
      this.alloc(10);

      while (true) {
         int digit = (int)(v & 127L);
         long next = v >>> 7;
         boolean last = next == 0L;
         if (!last) {
            digit |= -128;
         }

         this.data[this.pointer++] = (byte)digit;
         if (last) {
            return;
         }

         v = next;
      }
   }

   public void writeSignedLEB(long v) {
      this.alloc(10);

      boolean last;
      do {
         int digit = (int)(v & 127L);
         v >>= 7;
         last = v == 0L && (digit & 64) == 0 || v == -1L && (digit & 64) != 0;
         if (!last) {
            digit |= -128;
         }

         this.data[this.pointer++] = (byte)digit;
      } while (!last);
   }

   public void writeFixed(int v) {
      this.alloc(4);

      for (int i = 0; i < 4; i++) {
         this.data[this.pointer++] = (byte)v;
         v >>>= 8;
      }
   }

   public void writeFixed(long v) {
      this.alloc(8);

      for (int i = 0; i < 8; i++) {
         this.data[this.pointer++] = (byte)v;
         v >>>= 8;
      }
   }

   private void alloc(int size) {
      if (this.data.length - this.pointer < size) {
         int newLength = this.data.length * 2;
         if (newLength < this.pointer + size) {
            newLength = (this.pointer + size) * 2;
         }

         this.data = Arrays.copyOf(this.data, newLength);
      }
   }

   public byte[] getData() {
      return Arrays.copyOf(this.data, this.pointer);
   }
}
