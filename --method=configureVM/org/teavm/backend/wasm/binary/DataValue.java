package org.teavm.backend.wasm.binary;

public abstract class DataValue {
   private DataType type;

   DataValue(DataType type) {
      this.type = type;
   }

   public DataType getType() {
      return this.type;
   }

   public byte getByte(int index) {
      throw new UnsupportedOperationException();
   }

   public void setByte(int index, byte value) {
      throw new UnsupportedOperationException();
   }

   public short getShort(int index) {
      throw new UnsupportedOperationException();
   }

   public void setShort(int index, short value) {
      throw new UnsupportedOperationException();
   }

   public int getInt(int index) {
      throw new UnsupportedOperationException();
   }

   public void setInt(int index, int value) {
      throw new UnsupportedOperationException();
   }

   public long getLong(int index) {
      throw new UnsupportedOperationException();
   }

   public void setLong(int index, long value) {
      throw new UnsupportedOperationException();
   }

   public long getAddress(int index) {
      throw new UnsupportedOperationException();
   }

   public void setAddress(int index, long value) {
      throw new UnsupportedOperationException();
   }

   public float getFloat(int index) {
      throw new UnsupportedOperationException();
   }

   public void setFloat(int index, float value) {
      throw new UnsupportedOperationException();
   }

   public double getDouble(int index) {
      throw new UnsupportedOperationException();
   }

   public void setDouble(int index, double value) {
      throw new UnsupportedOperationException();
   }

   public DataValue getValue(int index) {
      throw new UnsupportedOperationException();
   }
}
