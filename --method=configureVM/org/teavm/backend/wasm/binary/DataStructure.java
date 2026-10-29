package org.teavm.backend.wasm.binary;

public class DataStructure extends DataType {
   private byte alignment;
   private DataType[] components;

   public DataStructure(byte alignment, DataType... components) {
      this.alignment = alignment;
      this.components = (DataType[])components.clone();
   }

   public byte getAlignment() {
      return this.alignment;
   }

   public DataType[] getComponents() {
      return (DataType[])this.components.clone();
   }

   @Override
   public DataValue createValue() {
      return new DataStructure.StructureValue(this);
   }

   static class StructureValue extends DataValue {
      private DataValue[] components;

      StructureValue(DataStructure type) {
         super(type);
         this.components = new DataValue[type.components.length];

         for (int i = 0; i < this.components.length; i++) {
            this.components[i] = type.components[i].createValue();
         }
      }

      @Override
      public void setByte(int index, byte value) {
         this.components[index].setByte(0, value);
      }

      @Override
      public void setShort(int index, short value) {
         this.components[index].setShort(0, value);
      }

      @Override
      public void setInt(int index, int value) {
         this.components[index].setInt(0, value);
      }

      @Override
      public void setLong(int index, long value) {
         this.components[index].setLong(0, value);
      }

      @Override
      public void setAddress(int index, long value) {
         this.components[index].setAddress(0, value);
      }

      @Override
      public void setFloat(int index, float value) {
         this.components[index].setFloat(0, value);
      }

      @Override
      public void setDouble(int index, double value) {
         this.components[index].setDouble(0, value);
      }

      @Override
      public DataValue getValue(int index) {
         return this.components[index];
      }
   }
}
