package org.teavm.model;

import java.util.Collections;
import java.util.List;

public class AnnotationValue {
   public static final byte BOOLEAN = 0;
   public static final byte BYTE = 1;
   public static final byte SHORT = 2;
   public static final byte INT = 3;
   public static final byte LONG = 4;
   public static final byte FLOAT = 5;
   public static final byte DOUBLE = 6;
   public static final byte STRING = 7;
   public static final byte CLASS = 8;
   public static final byte LIST = 9;
   public static final byte ENUM = 10;
   public static final byte ANNOTATION = 11;
   public static final byte CHAR = 12;
   private byte type;
   private Object value;

   public AnnotationValue(boolean value) {
      this.type = 0;
      this.value = value;
   }

   public AnnotationValue(byte value) {
      this.type = 1;
      this.value = value;
   }

   public AnnotationValue(char value) {
      this.type = 12;
      this.value = value;
   }

   public AnnotationValue(short value) {
      this.type = 2;
      this.value = value;
   }

   public AnnotationValue(int value) {
      this.type = 3;
      this.value = value;
   }

   public AnnotationValue(long value) {
      this.type = 4;
      this.value = value;
   }

   public AnnotationValue(float value) {
      this.type = 5;
      this.value = value;
   }

   public AnnotationValue(double value) {
      this.type = 6;
      this.value = value;
   }

   public AnnotationValue(String value) {
      this.type = 7;
      this.value = value;
   }

   public AnnotationValue(ValueType value) {
      this.type = 8;
      this.value = value;
   }

   public AnnotationValue(List<AnnotationValue> value) {
      this.type = 9;
      this.value = value;
   }

   public AnnotationValue(AnnotationReader value) {
      this.type = 11;
      this.value = value;
   }

   public AnnotationValue(FieldReference value) {
      this.type = 10;
      this.value = value;
   }

   public boolean getBoolean() {
      if (this.type != 0) {
         throw new IllegalStateException("There is no boolean value");
      } else {
         return (Boolean)this.value;
      }
   }

   public char getChar() {
      if (this.type != 12) {
         throw new IllegalStateException("There is no char value");
      } else {
         return (Character)this.value;
      }
   }

   public byte getByte() {
      if (this.type != 1) {
         throw new IllegalStateException("There is no byte value");
      } else {
         return (Byte)this.value;
      }
   }

   public short getShort() {
      if (this.type != 2) {
         throw new IllegalStateException("There is no short value");
      } else {
         return (Short)this.value;
      }
   }

   public int getInt() {
      if (this.type != 3) {
         throw new IllegalStateException("There is no int value");
      } else {
         return (Integer)this.value;
      }
   }

   public long getLong() {
      if (this.type != 4) {
         throw new IllegalStateException("There is no long value");
      } else {
         return (Long)this.value;
      }
   }

   public float getFloat() {
      if (this.type != 5) {
         throw new IllegalStateException("There is no float value");
      } else {
         return (Float)this.value;
      }
   }

   public double getDouble() {
      if (this.type != 6) {
         throw new IllegalStateException("There is no double value");
      } else {
         return (Double)this.value;
      }
   }

   public String getString() {
      if (this.type != 7) {
         throw new IllegalStateException("There is no String value");
      } else {
         return (String)this.value;
      }
   }

   public ValueType getJavaClass() {
      if (this.type != 8) {
         throw new IllegalStateException("There is no ValueType value");
      } else {
         return (ValueType)this.value;
      }
   }

   public List<AnnotationValue> getList() {
      if (this.type != 9) {
         throw new IllegalStateException("There is no List value");
      } else {
         return Collections.unmodifiableList((List<? extends AnnotationValue>)this.value);
      }
   }

   public FieldReference getEnumValue() {
      if (this.type != 10) {
         throw new IllegalStateException("There is no enum value");
      } else {
         return (FieldReference)this.value;
      }
   }

   public AnnotationReader getAnnotation() {
      if (this.type != 11) {
         throw new IllegalStateException("There is no annotation value");
      } else {
         return (AnnotationReader)this.value;
      }
   }

   public byte getType() {
      return this.type;
   }
}
