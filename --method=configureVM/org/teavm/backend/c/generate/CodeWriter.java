package org.teavm.backend.c.generate;

import org.teavm.model.ValueType;
import org.teavm.model.util.VariableType;

public abstract class CodeWriter {
   public abstract CodeWriter fragment();

   public CodeWriter println() {
      return this.println("");
   }

   public CodeWriter println(String string) {
      this.append(string);
      this.newLine();
      return this;
   }

   public CodeWriter print(String string) {
      this.append(string);
      return this;
   }

   public CodeWriter indent() {
      this.indentBy(1);
      return this;
   }

   public CodeWriter outdent() {
      this.indentBy(-1);
      return this;
   }

   public CodeWriter printType(ValueType type) {
      this.print(typeAsString(type));
      return this;
   }

   public CodeWriter printStrictType(ValueType type) {
      this.print(strictTypeAsString(type));
      return this;
   }

   public static String strictTypeAsString(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
               return "int8_t";
            case SHORT:
               return "int16_t";
            case CHARACTER:
               return "char16_t";
         }
      }

      return typeAsString(type);
   }

   public static String typeAsString(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case CHARACTER:
            case INTEGER:
               return "int32_t";
            case LONG:
               return "int64_t";
            case FLOAT:
               return "float";
            case DOUBLE:
               return "double";
         }
      } else {
         if (type instanceof ValueType.Array) {
            return "TeaVM_Array*";
         }

         if (type == ValueType.VOID) {
            return "void";
         }
      }

      return "void*";
   }

   public CodeWriter printType(VariableType type) {
      switch (type) {
         case INT:
            this.print("int32_t");
            break;
         case LONG:
            this.print("int64_t");
            break;
         case FLOAT:
            this.print("float");
            break;
         case DOUBLE:
            this.print("double");
            break;
         case OBJECT:
            this.print("void*");
            break;
         case BYTE_ARRAY:
         case CHAR_ARRAY:
         case SHORT_ARRAY:
         case INT_ARRAY:
         case LONG_ARRAY:
         case FLOAT_ARRAY:
         case DOUBLE_ARRAY:
         case OBJECT_ARRAY:
            this.print("TeaVM_Array*");
      }

      return this;
   }

   public CodeWriter printVolatileType(VariableType type) {
      switch (type) {
         case INT:
            this.print("volatile int32_t");
            break;
         case LONG:
            this.print("volatile int64_t");
            break;
         case FLOAT:
            this.print("volatile float");
            break;
         case DOUBLE:
            this.print("volatile double");
            break;
         case OBJECT:
            this.print("void * volatile");
            break;
         case BYTE_ARRAY:
         case CHAR_ARRAY:
         case SHORT_ARRAY:
         case INT_ARRAY:
         case LONG_ARRAY:
         case FLOAT_ARRAY:
         case DOUBLE_ARRAY:
         case OBJECT_ARRAY:
            this.print("TeaVM_Array * volatile");
      }

      return this;
   }

   protected abstract void newLine();

   protected abstract void append(String var1);

   protected abstract void indentBy(int var1);

   public abstract void flush();

   public abstract void source(String var1, int var2);

   public abstract void nosource();
}
