package org.teavm.backend.wasm.parser;

public class FunctionSectionParser extends BaseSectionParser {
   private final FunctionSectionListener listener;

   public FunctionSectionParser(FunctionSectionListener listener) {
      this.listener = listener;
   }

   @Override
   protected void parseContent() {
      int count = this.reader.readLEB();

      for (int i = 0; i < count; i++) {
         this.reportAddress();
         int typeIndex = this.reader.readLEB();
         this.listener.function(i, typeIndex);
      }
   }
}
