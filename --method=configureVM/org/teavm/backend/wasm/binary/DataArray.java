package org.teavm.backend.wasm.binary;

public class DataArray extends DataType {
   private DataType componentType;
   private int size;

   public DataArray(DataType componentType, int size) {
      this.componentType = componentType;
      this.size = size;
   }

   public DataType getComponentType() {
      return this.componentType;
   }

   public int getSize() {
      return this.size;
   }

   @Override
   public DataValue createValue() {
      if (this.componentType == DataPrimitives.BYTE) {
         return new DataArray.ByteArrayValue(this);
      } else if (this.componentType == DataPrimitives.SHORT) {
         return new DataArray.ShortArrayValue(this);
      } else if (this.componentType == DataPrimitives.INT) {
         return new DataArray.IntArrayValue(this);
      } else if (this.componentType == DataPrimitives.LONG) {
         return new DataArray.LongArrayValue(this);
      } else if (this.componentType == DataPrimitives.ADDRESS) {
         return new DataArray.AddressArrayValue(this);
      } else if (this.componentType == DataPrimitives.FLOAT) {
         return new DataArray.FloatArrayValue(this);
      } else {
         return this.componentType == DataPrimitives.DOUBLE ? new DataArray.DoubleArrayValue(this) : new DataArray.ArrayValue(this);
      }
   }

   static class AddressArrayValue extends DataValue {
      long[] data;

      AddressArrayValue(DataArray type) {
         super(type);
         this.data = new long[type.size];
      }

      @Override
      public long getAddress(int index) {
         return this.data[index];
      }

      @Override
      public void setAddress(int index, long value) {
         this.data[index] = value;
      }

      @Override
      public DataValue getValue(int index) {
         final int outerIndex = index;
         return new DataValue(DataPrimitives.ADDRESS) {
            @Override
            public long getAddress(int index) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               } else {
                  return AddressArrayValue.this.data[outerIndex];
               }
            }

            @Override
            public void setAddress(int index, long value) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               }

               AddressArrayValue.this.data[outerIndex] = value;
            }
         };
      }
   }

   static class ArrayValue extends DataValue {
      DataValue[] data;

      ArrayValue(DataArray type) {
         super(type);
         this.data = new DataValue[type.size];

         for (int i = 0; i < this.data.length; i++) {
            this.data[i] = type.componentType.createValue();
         }
      }

      @Override
      public byte getByte(int index) {
         return this.data[index].getByte(0);
      }

      @Override
      public void setByte(int index, byte value) {
         this.data[index].setByte(0, value);
      }

      @Override
      public short getShort(int index) {
         return this.data[index].getShort(0);
      }

      @Override
      public void setShort(int index, short value) {
         this.data[index].setShort(0, value);
      }

      @Override
      public int getInt(int index) {
         return this.data[index].getInt(0);
      }

      @Override
      public void setInt(int index, int value) {
         this.data[index].setInt(0, value);
      }

      @Override
      public long getLong(int index) {
         return this.data[index].getLong(0);
      }

      @Override
      public void setLong(int index, long value) {
         this.data[index].setLong(0, value);
      }

      @Override
      public long getAddress(int index) {
         return this.data[index].getAddress(0);
      }

      @Override
      public void setAddress(int index, long value) {
         this.data[index].setAddress(0, value);
      }

      @Override
      public float getFloat(int index) {
         return this.data[index].getFloat(0);
      }

      @Override
      public void setFloat(int index, float value) {
         this.data[index].setFloat(0, value);
      }

      @Override
      public double getDouble(int index) {
         return this.data[index].getDouble(0);
      }

      @Override
      public void setDouble(int index, double value) {
         this.data[index].setDouble(0, value);
      }

      @Override
      public DataValue getValue(int index) {
         return this.data[index];
      }
   }

   static class ByteArrayValue extends DataValue {
      byte[] data;

      ByteArrayValue(DataArray type) {
         super(type);
         this.data = new byte[type.size];
      }

      @Override
      public byte getByte(int index) {
         return this.data[index];
      }

      @Override
      public void setByte(int index, byte value) {
         this.data[index] = value;
      }

      @Override
      public DataValue getValue(int index) {
         final int outerIndex = index;
         return new DataValue(DataPrimitives.BYTE) {
            @Override
            public byte getByte(int index) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               } else {
                  return ByteArrayValue.this.data[outerIndex];
               }
            }

            @Override
            public void setByte(int index, byte value) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               }

               ByteArrayValue.this.data[outerIndex] = value;
            }
         };
      }
   }

   static class DoubleArrayValue extends DataValue {
      double[] data;

      DoubleArrayValue(DataArray type) {
         super(type);
         this.data = new double[type.size];
      }

      @Override
      public double getDouble(int index) {
         return this.data[index];
      }

      @Override
      public void setDouble(int index, double value) {
         this.data[index] = value;
      }

      @Override
      public DataValue getValue(int index) {
         final int outerIndex = index;
         return new DataValue(DataPrimitives.DOUBLE) {
            @Override
            public double getDouble(int index) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               } else {
                  return DoubleArrayValue.this.data[outerIndex];
               }
            }

            @Override
            public void setDouble(int index, double value) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               }

               DoubleArrayValue.this.data[outerIndex] = value;
            }
         };
      }
   }

   static class FloatArrayValue extends DataValue {
      float[] data;

      FloatArrayValue(DataArray type) {
         super(type);
         this.data = new float[type.size];
      }

      @Override
      public float getFloat(int index) {
         return this.data[index];
      }

      @Override
      public void setFloat(int index, float value) {
         this.data[index] = value;
      }

      @Override
      public DataValue getValue(int index) {
         final int outerIndex = index;
         return new DataValue(DataPrimitives.FLOAT) {
            @Override
            public float getFloat(int index) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               } else {
                  return FloatArrayValue.this.data[outerIndex];
               }
            }

            @Override
            public void setFloat(int index, float value) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               }

               FloatArrayValue.this.data[outerIndex] = value;
            }
         };
      }
   }

   static class IntArrayValue extends DataValue {
      int[] data;

      IntArrayValue(DataArray type) {
         super(type);
         this.data = new int[type.size];
      }

      @Override
      public int getInt(int index) {
         return this.data[index];
      }

      @Override
      public void setInt(int index, int value) {
         this.data[index] = value;
      }

      @Override
      public DataValue getValue(int index) {
         final int outerIndex = index;
         return new DataValue(DataPrimitives.INT) {
            @Override
            public int getInt(int index) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               } else {
                  return IntArrayValue.this.data[outerIndex];
               }
            }

            @Override
            public void setInt(int index, int value) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               }

               IntArrayValue.this.data[outerIndex] = value;
            }
         };
      }
   }

   static class LongArrayValue extends DataValue {
      long[] data;

      LongArrayValue(DataArray type) {
         super(type);
         this.data = new long[type.size];
      }

      @Override
      public long getLong(int index) {
         return this.data[index];
      }

      @Override
      public void setLong(int index, long value) {
         this.data[index] = value;
      }

      @Override
      public DataValue getValue(int index) {
         final int outerIndex = index;
         return new DataValue(DataPrimitives.LONG) {
            @Override
            public long getLong(int index) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               } else {
                  return LongArrayValue.this.data[outerIndex];
               }
            }

            @Override
            public void setLong(int index, long value) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               }

               LongArrayValue.this.data[outerIndex] = value;
            }
         };
      }
   }

   static class ShortArrayValue extends DataValue {
      short[] data;

      ShortArrayValue(DataArray type) {
         super(type);
         this.data = new short[type.size];
      }

      @Override
      public short getShort(int index) {
         return this.data[index];
      }

      @Override
      public void setShort(int index, short value) {
         this.data[index] = value;
      }

      @Override
      public DataValue getValue(int index) {
         final int outerIndex = index;
         return new DataValue(DataPrimitives.SHORT) {
            @Override
            public short getShort(int index) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               } else {
                  return ShortArrayValue.this.data[outerIndex];
               }
            }

            @Override
            public void setShort(int index, short value) {
               if (index != 0) {
                  throw new IllegalArgumentException("Index should be 0");
               }

               ShortArrayValue.this.data[outerIndex] = value;
            }
         };
      }
   }
}
