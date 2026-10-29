package org.teavm.backend.wasm.generate;

import java.util.HashMap;
import java.util.Map;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class WasmGCNameProvider {
   private ObjectIntMap<String> occupiedTopLevelIndexes = new ObjectIntHashMap();
   private ObjectIntMap<String> occupiedStructIndexes = new ObjectIntHashMap();
   private Map<MethodDescriptor, String> virtualMethodNames = new HashMap<>();
   private Map<FieldReference, String> memberFieldNames = new HashMap<>();

   public String topLevel(String name) {
      return this.pickUnoccupied(name);
   }

   public String structureField(String name) {
      return this.pickUnoccupied(name, this.occupiedStructIndexes);
   }

   public String forVirtualMethod(MethodDescriptor method) {
      return this.virtualMethodNames.computeIfAbsent(method, k -> this.pickUnoccupied(sanitize(k.getName()), this.occupiedStructIndexes));
   }

   public String forMemberField(FieldReference field) {
      return this.memberFieldNames.computeIfAbsent(field, k -> this.pickUnoccupied(sanitize(field.getFieldName()), this.occupiedStructIndexes));
   }

   public String suggestForMethod(MethodReference method) {
      StringBuilder sb = new StringBuilder();
      sb.append(sanitize(method.getClassName()));
      sb.append("::");
      sb.append(sanitize(method.getName()));
      return sb.toString();
   }

   public String suggestForStaticField(FieldReference field) {
      StringBuilder sb = new StringBuilder();
      sb.append(sanitize(field.getClassName()));
      sb.append('#');
      sb.append(sanitize(field.getFieldName()));
      return sb.toString();
   }

   public String suggestForClass(String className) {
      return sanitize(className);
   }

   public String suggestForArray(String item) {
      return "Array<" + item + ">";
   }

   public String suggestForType(ValueType type) {
      StringBuilder sb = new StringBuilder();
      this.suggestForType(type, sb);
      return sb.toString();
   }

   private void suggestForType(ValueType type, StringBuilder sb) {
      if (type instanceof ValueType.Object) {
         sb.append(this.suggestForClass(((ValueType.Object)type).getClassName()));
      } else if (type instanceof ValueType.Array) {
         sb.append("Array<");
         this.suggestForType(((ValueType.Array)type).getItemType(), sb);
         sb.append(">");
      } else {
         sb.append("&").append(type.toString());
      }
   }

   public static String sanitize(String name) {
      char[] chars = null;

      int i;
      for (i = 0; i < name.length(); i++) {
         char c = name.charAt(i);
         if (!isIdentifierPart(c)) {
            chars = new char[name.length()];
            name.getChars(0, i, chars, 0);
            chars[i++] = '_';
            break;
         }
      }

      if (chars == null) {
         return name;
      }

      while (i < name.length()) {
         char c = name.charAt(i);
         chars[i] = isIdentifierPart(c) ? c : '_';
         i++;
      }

      return new String(chars);
   }

   public static boolean isIdentifierPart(char c) {
      switch (c) {
         case '!':
         case '#':
         case '$':
         case '%':
         case '&':
         case '\'':
         case '*':
         case '+':
         case '-':
         case '.':
         case '/':
         case ':':
         case '<':
         case '=':
         case '>':
         case '?':
         case '@':
         case '\\':
         case '^':
         case '_':
         case '`':
         case '|':
         case '~':
            return true;
         case '"':
         case '(':
         case ')':
         case ',':
         case '0':
         case '1':
         case '2':
         case '3':
         case '4':
         case '5':
         case '6':
         case '7':
         case '8':
         case '9':
         case ';':
         case 'A':
         case 'B':
         case 'C':
         case 'D':
         case 'E':
         case 'F':
         case 'G':
         case 'H':
         case 'I':
         case 'J':
         case 'K':
         case 'L':
         case 'M':
         case 'N':
         case 'O':
         case 'P':
         case 'Q':
         case 'R':
         case 'S':
         case 'T':
         case 'U':
         case 'V':
         case 'W':
         case 'X':
         case 'Y':
         case 'Z':
         case '[':
         case ']':
         case 'a':
         case 'b':
         case 'c':
         case 'd':
         case 'e':
         case 'f':
         case 'g':
         case 'h':
         case 'i':
         case 'j':
         case 'k':
         case 'l':
         case 'm':
         case 'n':
         case 'o':
         case 'p':
         case 'q':
         case 'r':
         case 's':
         case 't':
         case 'u':
         case 'v':
         case 'w':
         case 'x':
         case 'y':
         case 'z':
         case '{':
         case '}':
         default:
            return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
      }
   }

   private String pickUnoccupied(String name) {
      return this.pickUnoccupied(name, this.occupiedTopLevelIndexes);
   }

   private String pickUnoccupied(String name, ObjectIntMap<String> occupiedIndexes) {
      String result = name;
      int index = occupiedIndexes.getOrDefault(name, -1);
      if (index >= 0) {
         result = name + "_" + index;
      }

      occupiedIndexes.put(name, ++index);
      return result;
   }
}
