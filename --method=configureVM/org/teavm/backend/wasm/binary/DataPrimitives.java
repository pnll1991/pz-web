package org.teavm.backend.wasm.binary;

public final class DataPrimitives {
   public static final DataType BYTE = new DataType() {
      @Override
      public DataValue createValue() {
         return new DataPrimitives.ByteDataValue();
      }
   };
   public static final DataType SHORT = new DataType() {
      @Override
      public DataValue createValue() {
         return new DataPrimitives.ShortDataValue();
      }
   };
   public static final DataType INT = new DataType() {
      @Override
      public DataValue createValue() {
         return new DataPrimitives.IntDataValue();
      }
   };
   public static final DataType LONG = new DataType() {
      @Override
      public DataValue createValue() {
         return new DataPrimitives.LongDataValue();
      }
   };
   public static final DataType ADDRESS = new DataType() {
      @Override
      public DataValue createValue() {
         return new DataPrimitives.AddressDataValue();
      }
   };
   public static final DataType FLOAT = new DataType() {
      @Override
      public DataValue createValue() {
         return new DataPrimitives.FloatDataValue();
      }
   };
   public static final DataType DOUBLE = new DataType() {
      @Override
      public DataValue createValue() {
         return new DataPrimitives.DoubleDataValue();
      }
   };

   private DataPrimitives() {
   }

   static class AddressDataValue extends DataValue {
      long value;

      AddressDataValue() {
         super(DataPrimitives.ADDRESS);
      }

      @Override
      public long getAddress(int index) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         } else {
            return this.value;
         }
      }

      @Override
      public void setAddress(int index, long value) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         }

         this.value = value;
      }
   }

   static class ByteDataValue extends DataValue {
      byte value;

      ByteDataValue() {
         super(DataPrimitives.BYTE);
      }

      @Override
      public byte getByte(int index) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         } else {
            return this.value;
         }
      }

      @Override
      public void setByte(int index, byte value) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         }

         this.value = value;
      }
   }

   static class DoubleDataValue extends DataValue {
      double value;

      DoubleDataValue() {
         super(DataPrimitives.DOUBLE);
      }

      @Override
      public double getDouble(int index) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         } else {
            return this.value;
         }
      }

      @Override
      public void setDouble(int index, double value) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         }

         this.value = value;
      }
   }

   static class FloatDataValue extends DataValue {
      float value;

      FloatDataValue() {
         super(DataPrimitives.FLOAT);
      }

      @Override
      public float getFloat(int index) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         } else {
            return this.value;
         }
      }

      @Override
      public void setFloat(int index, float value) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         }

         this.value = value;
      }
   }

   static class IntDataValue extends DataValue {
      int value;

      IntDataValue() {
         super(DataPrimitives.INT);
      }

      @Override
      public int getInt(int index) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         } else {
            return this.value;
         }
      }

      @Override
      public void setInt(int index, int value) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         }

         this.value = value;
      }
   }

   static class LongDataValue extends DataValue {
      long value;

      LongDataValue() {
         super(DataPrimitives.LONG);
      }

      @Override
      public long getLong(int index) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         } else {
            return this.value;
         }
      }

      @Override
      public void setLong(int index, long value) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         }

         this.value = value;
      }
   }

   static class ShortDataValue extends DataValue {
      short value;

      ShortDataValue() {
         super(DataPrimitives.SHORT);
      }

      @Override
      public short getShort(int index) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         } else {
            return this.value;
         }
      }

      @Override
      public void setShort(int index, short value) {
         if (index != 0) {
            throw new IllegalArgumentException("Index should be 0 for primitive values");
         }

         this.value = value;
      }
   }
}
