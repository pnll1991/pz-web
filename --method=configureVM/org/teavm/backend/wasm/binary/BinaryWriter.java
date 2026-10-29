package org.teavm.backend.wasm.binary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public class BinaryWriter {
   private int address;
   private List<DataValue> values = new ArrayList<>();

   public BinaryWriter(int start) {
      this.address = start;
   }

   public int append(DataValue value) {
      int result = align(this.address, this.getAlignment(value.getType()));
      this.values.add(value);
      this.address = this.offset(value.getType(), result);
      return result;
   }

   public int getAddress() {
      return this.address;
   }

   private int offset(DataType type, int index) {
      if (type == DataPrimitives.BYTE) {
         return index + 1;
      } else if (type == DataPrimitives.SHORT) {
         return align(index, 2) + 2;
      } else if (type == DataPrimitives.INT) {
         return align(index, 4) + 4;
      } else if (type == DataPrimitives.LONG) {
         return align(index, 8) + 8;
      } else if (type == DataPrimitives.FLOAT) {
         return align(index, 4) + 4;
      } else if (type == DataPrimitives.DOUBLE) {
         return align(index, 8) + 8;
      } else if (type == DataPrimitives.ADDRESS) {
         return align(index, 4) + 4;
      } else if (type instanceof DataArray array) {
         int next = this.offset(array.getComponentType(), index);
         return index + (next - index) * array.getSize();
      } else if (!(type instanceof DataStructure structure)) {
         return index;
      } else {
         if (structure.getAlignment() > 0) {
            index = align(index, structure.getAlignment());
         }

         DataType[] components = structure.getComponents();

         for (DataType component : components) {
            index = this.offset(component, index);
         }

         return index;
      }
   }

   public static int align(int address, int alignment) {
      return address == 0 ? 0 : ((address - 1) / alignment + 1) * alignment;
   }

   private int getAlignment(DataType type) {
      if (type == DataPrimitives.BYTE) {
         return 1;
      } else if (type == DataPrimitives.SHORT) {
         return 2;
      } else if (type == DataPrimitives.INT) {
         return 4;
      } else if (type == DataPrimitives.LONG) {
         return 8;
      } else if (type == DataPrimitives.FLOAT) {
         return 4;
      } else if (type == DataPrimitives.DOUBLE) {
         return 8;
      } else if (type == DataPrimitives.ADDRESS) {
         return 4;
      } else if (type instanceof DataArray) {
         return this.getAlignment(((DataArray)type).getComponentType());
      } else {
         return type instanceof DataStructure structure ? Math.max(structure.getAlignment(), this.getAlignment(structure.getComponents()[0])) : 1;
      }
   }

   public byte[] getData() {
      byte[] result = new byte[this.address];
      int offset = 0;

      for (DataValue value : this.values) {
         offset = this.writeData(result, offset, value);
      }

      return Arrays.copyOf(result, offset);
   }

   private int writeData(byte[] result, int offset, DataValue value) {
      DataType type = value.getType();
      if (type == DataPrimitives.BYTE) {
         result[offset++] = value.getByte(0);
      } else if (type == DataPrimitives.SHORT) {
         byte var17;
         offset = align(var17, 2);
         short v = value.getShort(0);
         result[offset++] = (byte)v;
         result[offset++] = (byte)(v >> 8);
      } else if (type == DataPrimitives.INT) {
         byte var18;
         offset = this.writeInt(var18, result, value.getInt(0));
      } else if (type == DataPrimitives.LONG) {
         byte var19;
         offset = this.writeLong(var19, result, value.getLong(0));
      } else if (type == DataPrimitives.FLOAT) {
         int v = Float.floatToRawIntBits(value.getFloat(0));
         byte var20;
         offset = this.writeInt(var20, result, v);
      } else if (type == DataPrimitives.DOUBLE) {
         long v = Double.doubleToRawLongBits(value.getDouble(0));
         byte var21;
         offset = this.writeLong(var21, result, v);
      } else if (type == DataPrimitives.ADDRESS) {
         byte var22;
         offset = this.writeInt(var22, result, (int)value.getAddress(0));
      } else if (type instanceof DataArray array) {
         for (int i = 0; i < array.getSize(); i++) {
            offset = this.writeData(result, offset, value.getValue(i));
         }
      } else if (type instanceof DataStructure structure) {
         if (structure.getAlignment() > 0) {
            byte var23;
            offset = align(var23, structure.getAlignment());
         }

         int componentCount = structure.getComponents().length;

         for (int i = 0; i < componentCount; i++) {
            offset = this.writeData(result, offset, value.getValue(i));
         }
      }

      return offset;
   }

   private int writeInt(int offset, byte[] result, int v) {
      offset = align(offset, 4);
      result[offset++] = (byte)v;
      result[offset++] = (byte)(v >> 8);
      result[offset++] = (byte)(v >> 16);
      result[offset++] = (byte)(v >> 24);
      return offset;
   }

   private int writeLong(int offset, byte[] result, long v) {
      offset = align(offset, 8);
      result[offset++] = (byte)v;
      result[offset++] = (byte)(v >> 8);
      result[offset++] = (byte)(v >> 16);
      result[offset++] = (byte)(v >> 24);
      result[offset++] = (byte)(v >> 32);
      result[offset++] = (byte)(v >> 40);
      result[offset++] = (byte)(v >> 48);
      result[offset++] = (byte)(v >> 56);
      return offset;
   }

   public <T> int writeMap(T[] keys, ToIntFunction<T> hashCodeF, ToLongFunction<T> keyWriter, ToLongFunction<T> valueWriter) {
      int tableSize = keys.length * 2;
      int maxTableSize = Math.min(keys.length * 5 / 2, tableSize + 10);
      Object[] bestTable = null;
      int bestCollisionRatio = 0;

      while (tableSize <= maxTableSize) {
         Object[] table = new Object[tableSize];
         int maxCollisionRatio = 0;

         for (T key : keys) {
            int hashCode = hashCodeF.applyAsInt(key);
            int collisionRatio = 0;

            while (true) {
               int index = mod(hashCode++, table.length);
               if (table[index] == null) {
                  table[index] = key;
                  maxCollisionRatio = Math.max(maxCollisionRatio, collisionRatio);
                  break;
               }

               collisionRatio++;
            }
         }

         if (bestTable == null || bestCollisionRatio > maxCollisionRatio) {
            bestCollisionRatio = maxCollisionRatio;
            bestTable = table;
         }

         tableSize++;
      }

      DataValue sizeValue = DataPrimitives.ADDRESS.createValue();
      int start = this.append(sizeValue);
      sizeValue.setAddress(0, bestTable.length);
      DataValue[] keyValues = new DataValue[bestTable.length];
      DataValue[] valueValues = new DataValue[bestTable.length];

      for (int i = 0; i < bestTable.length; i++) {
         DataValue keyValue = DataPrimitives.ADDRESS.createValue();
         DataValue valueValue = DataPrimitives.ADDRESS.createValue();
         this.append(keyValue);
         this.append(valueValue);
         keyValues[i] = keyValue;
         valueValues[i] = valueValue;
      }

      for (int i = 0; i < bestTable.length; i++) {
         T key = (T)bestTable[i];
         if (key != null) {
            keyValues[i].setAddress(0, keyWriter.applyAsLong(key));
            valueValues[i].setAddress(0, valueWriter.applyAsLong(key));
         }
      }

      return start;
   }

   private static int mod(int a, int b) {
      a %= b;
      if (a < 0) {
         a += b;
      }

      return a;
   }
}
