package org.teavm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class GenericValueType {
   private static final GenericValueType.Argument[] EMPTY_ARRAY = new GenericValueType.Argument[0];
   public static final GenericValueType.Void VOID = new GenericValueType.Void();
   public static final GenericValueType.Primitive BOOLEAN = new GenericValueType.Primitive(PrimitiveType.BOOLEAN);
   public static final GenericValueType.Primitive BYTE = new GenericValueType.Primitive(PrimitiveType.BYTE);
   public static final GenericValueType.Primitive SHORT = new GenericValueType.Primitive(PrimitiveType.SHORT);
   public static final GenericValueType.Primitive INT = new GenericValueType.Primitive(PrimitiveType.INTEGER);
   public static final GenericValueType.Primitive FLOAT = new GenericValueType.Primitive(PrimitiveType.FLOAT);
   public static final GenericValueType.Primitive LONG = new GenericValueType.Primitive(PrimitiveType.LONG);
   public static final GenericValueType.Primitive DOUBLE = new GenericValueType.Primitive(PrimitiveType.DOUBLE);
   public static final GenericValueType.Primitive CHAR = new GenericValueType.Primitive(PrimitiveType.CHARACTER);

   public abstract ValueType asValueType();

   private GenericValueType() {
   }

   abstract void toString(StringBuilder var1);

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      this.toString(sb);
      return sb.toString();
   }

   public static GenericValueType parse(String text, GenericValueType.ParsePosition position) {
      int i = position.index;
      if (i >= text.length()) {
         return null;
      }

      switch (text.charAt(i++)) {
         case 'B':
            position.index = i;
            return BYTE;
         case 'C':
            position.index = i;
            return CHAR;
         case 'D':
            position.index = i;
            return DOUBLE;
         case 'E':
         case 'G':
         case 'H':
         case 'K':
         case 'M':
         case 'N':
         case 'O':
         case 'P':
         case 'Q':
         case 'R':
         case 'U':
         case 'W':
         case 'X':
         case 'Y':
         default:
            return null;
         case 'F':
            position.index = i;
            return FLOAT;
         case 'I':
            position.index = i;
            return INT;
         case 'J':
            position.index = i;
            return LONG;
         case 'L':
            position.index = i;
            return parseObjectImpl(text, position);
         case 'S':
            position.index = i;
            return SHORT;
         case 'T':
            position.index = i;
            return parseVariable(text, position);
         case 'V':
            position.index = i;
            return VOID;
         case 'Z':
            position.index = i;
            return BOOLEAN;
         case '[':
            position.index = i;
            GenericValueType itemType = parse(text, position);
            return itemType != null ? new GenericValueType.Array(itemType) : null;
      }
   }

   private static GenericValueType parseObjectImpl(String text, GenericValueType.ParsePosition position) {
      int i = position.index;
      int last = i;
      GenericValueType.Object parent = null;

      while (i < text.length()) {
         char c = text.charAt(i);
         switch (c) {
            case '.':
               if (i == last) {
                  return null;
               }

               parent = new GenericValueType.Object(parent, text.substring(last, i).replace('/', '.'), EMPTY_ARRAY);
               last = ++i;
               break;
            case ';':
               if (last == i) {
                  return null;
               }

               String name = text.substring(last, i).replace('/', '.');
               position.index = ++i;
               return new GenericValueType.Object(parent, name, EMPTY_ARRAY);
            case '<':
               position.index = i + 1;
               GenericValueType.Argument[] arguments = parseArguments(text, position);
               if (arguments != null && position.index < text.length()) {
                  String name = text.substring(last, i).replace('/', '.');
                  GenericValueType.Object result = new GenericValueType.Object(parent, name, arguments);
                  i = position.index;
                  c = text.charAt(i++);
                  if (c == ';') {
                     position.index = i;
                     return result;
                  }

                  if (c != '.') {
                     return null;
                  }

                  parent = result;
                  last = i;
                  break;
               }

               return null;
            default:
               i++;
         }
      }

      return null;
   }

   private static GenericValueType.Argument[] parseArguments(String text, GenericValueType.ParsePosition position) {
      List<GenericValueType.Argument> arguments = new ArrayList<>();

      while (position.index < text.length()) {
         char c = text.charAt(position.index);
         switch (c) {
            case '*':
               position.index++;
               arguments.add(GenericValueType.Argument.ANY);
               break;
            case '+':
               position.index++;
               GenericValueType.Reference constraint = parseReference(text, position);
               if (constraint == null) {
                  return null;
               }

               arguments.add(GenericValueType.Argument.covariant(constraint));
               break;
            case '-':
               position.index++;
               GenericValueType.Reference constraint = parseReference(text, position);
               if (constraint == null) {
                  return null;
               }

               arguments.add(GenericValueType.Argument.contravariant(constraint));
               break;
            case '>':
               if (arguments.isEmpty()) {
                  return null;
               }

               position.index++;
               return arguments.toArray(new GenericValueType.Argument[0]);
            default:
               GenericValueType.Reference constraint = parseReference(text, position);
               if (constraint == null) {
                  return null;
               }

               arguments.add(GenericValueType.Argument.invariant(constraint));
         }
      }

      return null;
   }

   public static GenericValueType.Reference parseReference(String text, GenericValueType.ParsePosition position) {
      GenericValueType type = parse(text, position);
      return type instanceof GenericValueType.Reference ? (GenericValueType.Reference)type : null;
   }

   public static GenericValueType.Object parseObject(String text, GenericValueType.ParsePosition position) {
      GenericValueType type = parse(text, position);
      return type instanceof GenericValueType.Object ? (GenericValueType.Object)type : null;
   }

   private static GenericValueType parseVariable(String text, GenericValueType.ParsePosition position) {
      for (int i = position.index; i < text.length(); i++) {
         if (text.charAt(i) == ';') {
            if (i == position.index) {
               return null;
            }

            GenericValueType.Variable result = new GenericValueType.Variable(text.substring(position.index, i));
            position.index = i + 1;
            return result;
         }
      }

      return null;
   }

   public GenericValueType parse(String text) {
      GenericValueType.ParsePosition position = new GenericValueType.ParsePosition();
      GenericValueType type = parse(text, position);
      return position.index == text.length() ? type : null;
   }

   public abstract boolean canBeRepresentedAsRaw();

   public static class Argument {
      public static final GenericValueType.Argument ANY = new GenericValueType.Argument(GenericValueType.ArgumentKind.ANY, null);
      private final GenericValueType.ArgumentKind kind;
      private final GenericValueType.Reference value;
      private int hash;

      private Argument(GenericValueType.ArgumentKind kind, GenericValueType.Reference value) {
         this.kind = kind;
         this.value = value;
      }

      public GenericValueType.ArgumentKind getKind() {
         return this.kind;
      }

      public GenericValueType.Reference getValue() {
         return this.value;
      }

      public static GenericValueType.Argument invariant(GenericValueType.Reference value) {
         return new GenericValueType.Argument(GenericValueType.ArgumentKind.INVARIANT, value);
      }

      public static GenericValueType.Argument covariant(GenericValueType.Reference value) {
         return new GenericValueType.Argument(GenericValueType.ArgumentKind.COVARIANT, value);
      }

      public static GenericValueType.Argument contravariant(GenericValueType.Reference value) {
         return new GenericValueType.Argument(GenericValueType.ArgumentKind.CONTRAVARIANT, value);
      }

      @Override
      public int hashCode() {
         if (this.hash == 0) {
            switch (this.kind) {
               case COVARIANT:
                  this.hash = 4379540;
                  break;
               case CONTRAVARIANT:
                  this.hash = 6993579;
                  break;
               case INVARIANT:
                  this.hash = 72320251;
                  break;
               case ANY:
                  this.hash = 43866465;
            }
         }

         if (this.value != null) {
            this.hash = this.hash * 47 + this.value.hashCode();
         }

         return this.hash;
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         return this == obj ? true : super.equals(obj);
      }
   }

   public enum ArgumentKind {
      COVARIANT,
      CONTRAVARIANT,
      INVARIANT,
      ANY;
   }

   public static final class Array extends GenericValueType.Reference {
      private final GenericValueType itemType;
      private int hash;

      public Array(GenericValueType itemType) {
         this.itemType = itemType;
      }

      public GenericValueType getItemType() {
         return this.itemType;
      }

      @Override
      public ValueType asValueType() {
         ValueType item = this.itemType.asValueType();
         return item != null ? ValueType.arrayOf(item) : null;
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         if (this == obj) {
            return true;
         } else {
            return !(obj instanceof GenericValueType.Array that) ? false : this.itemType.equals(that.itemType);
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

      @Override
      void toString(StringBuilder sb) {
         sb.append("[");
         this.itemType.toString(sb);
      }

      @Override
      public boolean canBeRepresentedAsRaw() {
         return this.itemType.canBeRepresentedAsRaw();
      }
   }

   public static final class Object extends GenericValueType.Reference {
      private final GenericValueType.Object parent;
      private final String className;
      private final GenericValueType.Argument[] arguments;
      private int hash;

      public Object(GenericValueType.Object parent, String className, GenericValueType.Argument[] arguments) {
         this.parent = parent;
         this.className = className;
         this.arguments = arguments != null ? (GenericValueType.Argument[])arguments.clone() : GenericValueType.EMPTY_ARRAY;
      }

      public GenericValueType.Object getParent() {
         return this.parent;
      }

      public String getClassName() {
         return this.className;
      }

      public String getFullClassName() {
         return this.parent != null ? this.parent.getFullClassName() + "$" + this.className : this.className;
      }

      public GenericValueType.Argument[] getArguments() {
         return this.arguments.length == 0 ? GenericValueType.EMPTY_ARRAY : (GenericValueType.Argument[])this.arguments.clone();
      }

      @Override
      public ValueType asValueType() {
         return this.canBeRepresentedAsRaw() ? ValueType.object(this.getClassName()) : null;
      }

      @Override
      void toString(StringBuilder sb) {
         sb.append("L");
         this.toStringImpl(sb);
         sb.append(";");
      }

      private void toStringImpl(StringBuilder sb) {
         if (this.parent != null) {
            this.parent.toStringImpl(sb);
            sb.append(".");
         }

         sb.append(this.className.replace('.', '/'));
         if (this.arguments.length > 0) {
            sb.append("<");

            for (GenericValueType.Argument argument : this.arguments) {
               switch (argument.kind) {
                  case COVARIANT:
                     sb.append("+");
                     break;
                  case CONTRAVARIANT:
                     sb.append("-");
                  case INVARIANT:
                  default:
                     break;
                  case ANY:
                     sb.append("*");
               }

               if (argument.value != null) {
                  argument.value.toString(sb);
               }
            }

            sb.append(">");
         }
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         if (obj == this) {
            return true;
         } else if (!(obj instanceof GenericValueType.Object that)) {
            return false;
         } else {
            if (that.arguments.length != this.arguments.length) {
               return false;
            }

            if (!that.className.equals(this.className)) {
               return false;
            }

            for (int i = 0; i < this.arguments.length; i++) {
               if (!this.arguments[i].equals(that.arguments[i])) {
                  return false;
               }
            }

            return Objects.equals(this.parent, that.parent);
         }
      }

      @Override
      public int hashCode() {
         if (this.hash == 0) {
            this.hash = 85396296 ^ this.className.hashCode() * 167;

            for (GenericValueType.Argument arg : this.arguments) {
               this.hash = this.hash * 31 + arg.hashCode();
            }

            if (this.parent != null) {
               this.hash = 167 * this.hash + this.parent.hashCode();
            }

            if (this.hash == 0) {
               this.hash++;
            }
         }

         return this.hash;
      }

      @Override
      public boolean canBeRepresentedAsRaw() {
         return (this.arguments == null || this.arguments.length == 0) && (this.parent == null || this.parent.canBeRepresentedAsRaw());
      }
   }

   public static class ParsePosition {
      public int index;
   }

   public static final class Primitive extends GenericValueType {
      private final PrimitiveType kind;
      private final int hash;

      private Primitive(PrimitiveType kind) {
         this.kind = kind;
         this.hash = 17988782 ^ kind.ordinal() * 31;
      }

      @Override
      public ValueType asValueType() {
         return ValueType.primitive(this.kind);
      }

      public PrimitiveType getKind() {
         return this.kind;
      }

      @Override
      void toString(StringBuilder sb) {
         switch (this.kind) {
            case BOOLEAN:
               sb.append("Z");
               break;
            case BYTE:
               sb.append("B");
               break;
            case SHORT:
               sb.append("S");
               break;
            case INTEGER:
               sb.append("I");
               break;
            case LONG:
               sb.append("J");
               break;
            case FLOAT:
               sb.append("F");
               break;
            case CHARACTER:
               sb.append("C");
               break;
            case DOUBLE:
               sb.append("D");
         }
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         return this == obj;
      }

      @Override
      public int hashCode() {
         return this.hash;
      }

      @Override
      public boolean canBeRepresentedAsRaw() {
         return true;
      }
   }

   public abstract static class Reference extends GenericValueType {
      private Reference() {
      }
   }

   public static final class Variable extends GenericValueType.Reference {
      private final String name;
      private int hash;

      public Variable(String name) {
         this.name = name;
      }

      public String getName() {
         return this.name;
      }

      @Override
      public ValueType asValueType() {
         return null;
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         if (obj == this) {
            return true;
         } else {
            return !(obj instanceof GenericValueType.Variable that) ? false : this.name.equals(that.name);
         }
      }

      @Override
      public int hashCode() {
         if (this.hash == 0) {
            this.hash = 69257681 ^ this.name.hashCode() * 173;
            if (this.hash == 0) {
               this.hash++;
            }
         }

         return this.hash;
      }

      @Override
      void toString(StringBuilder sb) {
         sb.append("T");
         sb.append(this.name);
         sb.append(";");
      }

      @Override
      public boolean canBeRepresentedAsRaw() {
         return false;
      }
   }

   public static final class Void extends GenericValueType {
      private Void() {
      }

      @Override
      public ValueType asValueType() {
         return ValueType.VOID;
      }

      @Override
      void toString(StringBuilder sb) {
         sb.append("V");
      }

      @Override
      public boolean equals(java.lang.Object obj) {
         return this == obj;
      }

      @Override
      public int hashCode() {
         return 53604390;
      }

      @Override
      public boolean canBeRepresentedAsRaw() {
         return true;
      }
   }
}
