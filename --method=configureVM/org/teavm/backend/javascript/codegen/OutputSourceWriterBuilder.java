package org.teavm.backend.javascript.codegen;

public class OutputSourceWriterBuilder {
   private NamingStrategy naming;
   private boolean minified;
   private int lineWidth = 512;

   public OutputSourceWriterBuilder(NamingStrategy naming) {
      this.naming = naming;
   }

   public boolean isMinified() {
      return this.minified;
   }

   public void setMinified(boolean minified) {
      this.minified = minified;
   }

   public void setLineWidth(int lineWidth) {
      this.lineWidth = lineWidth;
   }

   public OutputSourceWriter build(Appendable innerWriter) {
      OutputSourceWriter writer = new OutputSourceWriter(this.naming, innerWriter, this.lineWidth);
      writer.setMinified(this.minified);
      return writer;
   }
}
