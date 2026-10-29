package org.teavm.cache;

public final class FileNameEncoder {
   private FileNameEncoder() {
   }

   public static String encodeFileName(String name) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);
         switch (c) {
            case ' ':
               sb.append("$w");
               break;
            case '"':
               sb.append("$Q");
               break;
            case '$':
               sb.append("$$");
               break;
            case '%':
               sb.append("$p");
               break;
            case '*':
               sb.append("$a");
               break;
            case '.':
               sb.append("$d");
               break;
            case '/':
               sb.append("$s");
               break;
            case ':':
               sb.append("$c");
               break;
            case '<':
               sb.append("$l");
               break;
            case '>':
               sb.append("$g");
               break;
            case '?':
               sb.append("$q");
               break;
            case '\\':
               sb.append("$b");
               break;
            case '|':
               sb.append("$v");
               break;
            default:
               sb.append(c);
         }
      }

      String str = sb.toString();
      sb.setLength(0);

      for (int i = 0; i < str.length(); i += 100) {
         if (i > 0) {
            sb.append('/');
         }

         int j = Math.min(i + 100, str.length());
         sb.append(str.substring(i, j));
      }

      return sb.toString();
   }
}
