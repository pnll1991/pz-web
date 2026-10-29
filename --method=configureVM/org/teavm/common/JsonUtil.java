package org.teavm.common;

import java.io.IOException;
import java.io.Writer;

public final class JsonUtil {
   private JsonUtil() {
   }

   public static void writeEscapedString(Writer output, String str) throws IOException {
      for (int i = 0; i < str.length(); i++) {
         char c = str.charAt(i);
         switch (c) {
            case '\b':
               output.write("\\b");
               break;
            case '\t':
               output.write("\\t");
               break;
            case '\n':
               output.write("\\n");
               break;
            case '\r':
               output.write("\\r");
               break;
            case '"':
               output.write("\\\"");
               break;
            case '\\':
               output.write("\\\\");
               break;
            default:
               output.write(c);
         }
      }
   }
}
