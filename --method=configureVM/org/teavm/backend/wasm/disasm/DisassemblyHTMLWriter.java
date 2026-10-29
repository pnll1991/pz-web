package org.teavm.backend.wasm.disasm;

import java.io.PrintWriter;

public class DisassemblyHTMLWriter extends DisassemblyWriter {
   public DisassemblyHTMLWriter(PrintWriter out) {
      super(out);
   }

   @Override
   public DisassemblyWriter prologue() {
      this.writeExact("<html>\n<head>");
      this.writeExact("<style>\n");
      this.writeExact("em { color: gray; }\n");
      this.writeExact("</style></head>\n");
      return this.writeExact("<body><pre>");
   }

   @Override
   public DisassemblyWriter epilogue() {
      return this.writeExact("</pre></body></html>");
   }

   @Override
   public DisassemblyWriter startLink(String s) {
      this.writeExact("<a href=\"#").writeExact(s).writeExact("\">");
      return this;
   }

   @Override
   public DisassemblyWriter endLink() {
      this.writeExact("</a>");
      return this;
   }

   @Override
   public DisassemblyWriter startLinkTarget(String s) {
      this.writeExact("<a name=\"").writeExact(s).writeExact("\">");
      return this;
   }

   @Override
   public DisassemblyWriter endLinkTarget() {
      this.writeExact("</a>");
      return this;
   }

   @Override
   public DisassemblyWriter write(String s) {
      StringBuilder sb = null;

      int i;
      for (i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == '<') {
            sb = new StringBuilder();
            sb.append(s, 0, i);
            break;
         }
      }

      if (sb != null) {
         for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
               sb.append("&lt;");
            } else {
               sb.append(c);
            }
         }

         s = sb.toString();
      }

      this.writeExact(s);
      return this;
   }

   @Override
   protected void startAnnotation() {
      this.writeExact("<em>");
   }

   @Override
   protected void endAnnotation() {
      this.writeExact("</em>");
   }
}
