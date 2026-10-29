package org.teavm.backend.c.generate;

import org.teavm.model.ValueType;

public class SimpleFileNameProvider implements FileNameProvider {
   @Override
   public String fileName(String className) {
      StringBuilder sb = new StringBuilder("classes/");
      escape(className, sb);
      return sb.toString();
   }

   @Override
   public String fileName(ValueType type) {
      StringBuilder sb = new StringBuilder();
      fileNameRec(type, sb);
      return sb.toString();
   }

   private static void fileNameRec(ValueType type, StringBuilder sb) {
      if (type instanceof ValueType.Object) {
         sb.append("classes/");
         escape(((ValueType.Object)type).getClassName(), sb);
      } else if (type instanceof ValueType.Array) {
         sb.append("arrays/");
         fileNameRec(((ValueType.Array)type).getItemType(), sb);
      } else if (type instanceof ValueType.Primitive) {
         sb.append("primitives/");
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               sb.append("boolean");
               break;
            case BYTE:
               sb.append("byte");
               break;
            case SHORT:
               sb.append("short");
               break;
            case CHARACTER:
               sb.append("char");
               break;
            case INTEGER:
               sb.append("int");
               break;
            case LONG:
               sb.append("long");
               break;
            case FLOAT:
               sb.append("float");
               break;
            case DOUBLE:
               sb.append("double");
         }
      } else if (type == ValueType.VOID) {
         sb.append("primitives/void");
      }
   }

   @Override
   public String escapeName(String name) {
      StringBuilder sb = new StringBuilder();
      escape(name, sb);
      return sb.toString();
   }

   private static void escape(String className, StringBuilder sb) {
      for (int i = 0; i < className.length(); i++) {
         char c = className.charAt(i);
         switch (c) {
            case '"':
               sb.append("@q");
               break;
            case '$':
               sb.append("@d");
               break;
            case '*':
               sb.append("@m");
               break;
            case '.':
               sb.append('/');
               break;
            case '/':
               sb.append("@s");
               break;
            case ':':
               sb.append("@c");
               break;
            case ';':
               sb.append("@e");
               break;
            case '<':
               sb.append("@l");
               break;
            case '>':
               sb.append("@g");
               break;
            case '@':
               sb.append("@@");
               break;
            case '\\':
               sb.append("@b");
               break;
            case '|':
               sb.append("@p");
               break;
            default:
               sb.append(c);
         }
      }
   }
}
