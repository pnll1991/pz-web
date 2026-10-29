package org.teavm.model;

public final class RuntimeConstant {
   public static final byte INT = 0;
   public static final byte LONG = 1;
   public static final byte FLOAT = 2;
   public static final byte DOUBLE = 3;
   public static final byte STRING = 4;
   public static final byte TYPE = 5;
   public static final byte METHOD = 6;
   public static final byte METHOD_HANDLE = 7;
   public static final byte DYNAMIC_CONSTANT = 8;
   private byte kind;
   private Object value;

   RuntimeConstant(byte kind, Object value) {
      this.kind = kind;
      this.value = value;
   }

   public RuntimeConstant(int value) {
      this((byte)0, value);
   }

   public RuntimeConstant(long value) {
      this((byte)1, value);
   }

   public RuntimeConstant(float value) {
      this((byte)2, value);
   }

   public RuntimeConstant(double value) {
      this((byte)2, value);
   }

   public RuntimeConstant(String value) {
      this((byte)4, value);
   }

   public RuntimeConstant(ValueType value) {
      this((byte)5, value);
   }

   public RuntimeConstant(ValueType[] methodType) {
      this((byte)6, methodType.clone());
   }

   public RuntimeConstant(MethodHandle value) {
      this((byte)7, value);
   }

   public RuntimeConstant(DynamicConstant value) {
      this((byte)8, value);
   }

   public byte getKind() {
      return this.kind;
   }

   public int getInt() {
      return (Integer)this.value;
   }

   public long getLong() {
      return (Long)this.value;
   }

   public float getFloat() {
      return (Float)this.value;
   }

   public double getDouble() {
      return (Double)this.value;
   }

   public String getString() {
      return (String)this.value;
   }

   public ValueType getValueType() {
      return (ValueType)this.value;
   }

   public ValueType[] getMethodType() {
      return (ValueType[])((ValueType[])this.value).clone();
   }

   public MethodHandle getMethodHandle() {
      return (MethodHandle)this.value;
   }

   public DynamicConstant getDynamicConstant() {
      return (DynamicConstant)this.value;
   }
}
