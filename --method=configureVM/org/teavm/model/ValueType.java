package org.teavm.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract sealed class ValueType implements Serializable permits ValueType.Object, ValueType.Array, ValueType.Primitive, ValueType.Void {
   private static final Map<Class<?>, ValueType> primitiveMap = new HashMap<>();
   public static final ValueType.Void VOID = new ValueType.Void();
   public static final ValueType.Primitive BOOLEAN = new ValueType.Primitive(PrimitiveType.BOOLEAN, object(Boolean.class.getName()));
   public static final ValueType.Primitive BYTE = new ValueType.Primitive(PrimitiveType.BYTE, object(Byte.class.getName()));
   public static final ValueType.Primitive SHORT = new ValueType.Primitive(PrimitiveType.SHORT, object(Short.class.getName()));
   public static final ValueType.Primitive INTEGER = new ValueType.Primitive(PrimitiveType.INTEGER, object(Integer.class.getName()));
   public static final ValueType.Primitive FLOAT = new ValueType.Primitive(PrimitiveType.FLOAT, object(Float.class.getName()));
   public static final ValueType.Primitive LONG = new ValueType.Primitive(PrimitiveType.LONG, object(Long.class.getName()));
   public static final ValueType.Primitive DOUBLE = new ValueType.Primitive(PrimitiveType.DOUBLE, object(Double.class.getName()));
   public static final ValueType.Primitive CHARACTER = new ValueType.Primitive(PrimitiveType.CHARACTER, object(Character.class.getName()));

   private ValueType() {
   }

   public static ValueType.Object object(String cls) {
      return new ValueType.Object(cls);
   }

   public static ValueType.Array arrayOf(ValueType type) {
      return new ValueType.Array(type);
   }

   public static ValueType primitive(PrimitiveType type) {
      return switch (type) {
         case BOOLEAN -> BOOLEAN;
         case BYTE -> BYTE;
         case SHORT -> SHORT;
         case INTEGER -> INTEGER;
         case LONG -> LONG;
         case FLOAT -> FLOAT;
         case CHARACTER -> CHARACTER;
         case DOUBLE -> DOUBLE;
      };
   }

   public static ValueType[] parseMany(String text) {
      ValueType[] types = parseManyIfPossible(text);
      if (types == null) {
         throw new IllegalArgumentException("Illegal method type signature: " + text);
      } else {
         return types;
      }
   }

   public static ValueType[] parseManyIfPossible(String text) {
      List<ValueType> types = new ArrayList<>();
      int index = 0;

      while (index < text.length()) {
         int nextIndex = cut(text, index);
         ValueType type = parseIfPossible(text.substring(index, nextIndex));
         if (type == null) {
            return null;
         }

         types.add(type);
         index = nextIndex;
      }

      return types.toArray(new ValueType[0]);
   }

   private static int cut(String text, int index) {
      while (text.charAt(index) == '[') {
         if (++index >= text.length()) {
            return index;
         }
      }

      if (text.charAt(index) != 'L') {
         return index + 1;
      }

      while (text.charAt(index) != ';') {
         if (++index >= text.length()) {
            return index;
         }
      }

      return index + 1;
   }

   public static ValueType parse(String string) {
      ValueType type = parseIfPossible(string);
      if (type == null) {
         throw new IllegalArgumentException("Illegal type signature: " + string);
      } else {
         return type;
      }
   }

   public static ValueType parseIfPossible(String string) {
      int arrayDegree = 0;

      int left;
      for (left = 0; string.charAt(left) == '['; left++) {
         arrayDegree++;
      }

      string = string.substring(left);
      if (string.isEmpty()) {
         return null;
      }

      ValueType type = parseImpl(string);
      if (type == null) {
         return null;
      }

      while (arrayDegree-- > 0) {
         type = arrayOf(type);
      }

      return type;
   }

   public static String manyToString(ValueType[] types) {
      StringBuilder sb = new StringBuilder();

      for (ValueType type : types) {
         sb.append(type);
      }

      return sb.toString();
   }

   public static String methodTypeToString(ValueType[] types) {
      return "(" + Arrays.stream(types, 0, types.length - 1).map(java.lang.Object::toString).collect(Collectors.joining()) + ")" + types[types.length - 1];
   }

   public abstract boolean isObject(String var1);

   public boolean isObject(Class<?> cls) {
      return this.isObject(cls.getName());
   }

   private static ValueType parseImpl(String string) {
      return switch (string.charAt(0)) {
         case 'B' -> primitive(PrimitiveType.BYTE);
         case 'C' -> primitive(PrimitiveType.CHARACTER);
         case 'D' -> primitive(PrimitiveType.DOUBLE);
         default -> null;
         case 'F' -> primitive(PrimitiveType.FLOAT);
         case 'I' -> primitive(PrimitiveType.INTEGER);
         case 'J' -> primitive(PrimitiveType.LONG);
         case 'L' -> !string.endsWith(";") ? null : object(string.substring(1, string.length() - 1).replace('/', '.'));
         case 'S' -> primitive(PrimitiveType.SHORT);
         case 'V' -> VOID;
         case 'Z' -> primitive(PrimitiveType.BOOLEAN);
      };
   }

   public boolean isSubtypeOf(ValueType supertype) {
      if (supertype instanceof ValueType.Object) {
         return !(this instanceof ValueType.Primitive);
      } else {
         return supertype instanceof ValueType.Array && this instanceof ValueType.Array
            ? ((ValueType.Array)this).getItemType().isSubtypeOf(((ValueType.Array)supertype).getItemType())
            : false;
      }
   }

   public static ValueType parse(Class<?> cls) {
      if (cls.isPrimitive()) {
         return primitiveMap.get(cls);
      } else {
         return cls.getComponentType() != null ? arrayOf(parse(cls.getComponentType())) : object(cls.getName());
      }
   }

   static {
      primitiveMap.put(boolean.class, BOOLEAN);
      primitiveMap.put(char.class, CHARACTER);
      primitiveMap.put(byte.class, BYTE);
      primitiveMap.put(short.class, SHORT);
      primitiveMap.put(int.class, INTEGER);
      primitiveMap.put(long.class, LONG);
      primitiveMap.put(float.class, FLOAT);
      primitiveMap.put(double.class, DOUBLE);
      primitiveMap.put(void.class, VOID);
   }

   public static final class Array extends ValueType {
      private ValueType itemType;
      private transient int hash;

      public Array(ValueType itemType) {
         this.itemType = itemType;
      }

      public ValueType getItemType() {
         return this.itemType;
      }

      @Override
      public String toString() {
         return "[" + this.itemType;
      }

      @Override
      public boolean isObject(String cls) {
         return false;
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         if (this == obj) {
            return true;
         } else {
            return obj instanceof ValueType.Array that ? this.itemType.equals(that.itemType) : false;
         }
      }

      @Override
      public int hashCode() {
         if (this.hash == 0) {
            this.hash = 27039876 ^ this.itemType.hashCode() * 193;
            if (this.hash == 0) {
               this.hash++;
            }
         }

         return this.hash;
      }
   }

   public static final class Object extends ValueType {
      private String className;
      private transient int hash;

      public Object(String className) {
         this.className = className;
      }

      public String getClassName() {
         return this.className;
      }

      @Override
      public String toString() {
         return "L" + this.className.replace('.', '/') + ";";
      }

      @Override
      public boolean isObject(String className) {
         return this.className.equals(className);
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         if (obj == this) {
            return true;
         } else {
            return obj instanceof ValueType.Object that ? that.className.equals(this.className) : false;
         }
      }

      @Override
      public int hashCode() {
         if (this.hash == 0) {
            this.hash = 85396296 ^ this.className.hashCode() * 167;
            if (this.hash == 0) {
               this.hash++;
            }
         }

         return this.hash;
      }
   }

   public static final class Primitive extends ValueType {
      private PrimitiveType kind;
      private final ValueType.Object boxedType;
      private int hash;

      private Primitive(PrimitiveType kind, ValueType.Object boxedType) {
         this.kind = kind;
         this.boxedType = boxedType;
         this.hash = 17988782 ^ kind.ordinal() * 31;
      }

      public PrimitiveType getKind() {
         return this.kind;
      }

      public ValueType.Object getBoxedType() {
         return this.boxedType;
      }

      @Override
      public String toString() {
         return switch (this.kind) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case SHORT -> "S";
            case INTEGER -> "I";
            case LONG -> "J";
            case FLOAT -> "F";
            case CHARACTER -> "C";
            case DOUBLE -> "D";
         };
      }

      @Override
      public boolean isObject(String cls) {
         return false;
      }

      @Override
      public int hashCode() {
         return this.hash;
      }
   }

   public static final class Void extends ValueType {
      private Void() {
      }

      @Override
      public String toString() {
         return "V";
      }

      @Override
      public boolean isObject(String cls) {
         return false;
      }

      @Override
      public int hashCode() {
         return 53604390;
      }
   }
}
