package org.teavm.backend.wasm.parser;

public class GlobalSectionParser extends BaseSectionParser {
   private final GlobalSectionListener listener;
   private CodeParser codeParser;
   private int globalIndexOffset;

   public GlobalSectionParser(GlobalSectionListener listener) {
      this.listener = listener;
      this.codeParser = new CodeParser();
   }

   public void setGlobalIndexOffset(int globalIndexOffset) {
      this.globalIndexOffset = globalIndexOffset;
   }

   @Override
   protected void parseContent() {
      int count = this.readLEB();

      for (int i = 0; i < count; i++) {
         this.reportAddress();
         WasmHollowType type = this.reader.readType();
         boolean mutable = this.reader.data[this.reader.ptr++] != 0;
         CodeListener codeListener = this.listener.startGlobal(i + this.globalIndexOffset, type, mutable);
         if (codeListener == null) {
            codeListener = CodeListener.EMPTY;
         }

         this.codeParser.setCodeListener(codeListener);
         if (!this.codeParser.parseSingleExpression(this.reader)) {
            throw new ParseException("Error parsing global initializer", this.reader.ptr);
         }

         this.reader.ptr++;
         this.listener.endGlobal();
      }
   }
}
