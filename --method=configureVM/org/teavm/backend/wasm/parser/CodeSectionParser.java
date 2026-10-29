package org.teavm.backend.wasm.parser;

public class CodeSectionParser extends BaseSectionParser {
   private CodeSectionListener listener;
   private int functionIndexOffset;
   private CodeParser codeParser = new CodeParser();

   public CodeSectionParser(CodeSectionListener listener) {
      this.listener = listener;
   }

   public void setFunctionIndexOffset(int functionIndexOffset) {
      this.functionIndexOffset = functionIndexOffset;
   }

   @Override
   protected void parseContent() {
      this.reportAddress();
      int count = this.readLEB();
      this.listener.sectionStart(count);

      for (int i = 0; i < count; i++) {
         this.parseFunction(i);
      }

      this.reportAddress();
      this.listener.sectionEnd();
   }

   private void parseFunction(int index) {
      this.reportAddress();
      int functionSize = this.readLEB();
      int end = this.reader.ptr + functionSize;
      if (this.listener.functionStart(index + this.functionIndexOffset, functionSize)) {
         this.parseLocals();
         CodeListener codeListener = this.listener.code();
         if (codeListener != null) {
            this.codeParser.setCodeListener(codeListener);
            this.codeParser.parse(this.reader);
         }
      }

      this.reader.ptr = end;
      this.reportAddress();
      this.listener.functionEnd();
   }

   private void parseLocals() {
      this.reportAddress();
      int localEntries = this.readLEB();
      this.listener.localsStart(localEntries);
      int localIndex = 0;

      for (int i = 0; i < localEntries; i++) {
         this.reportAddress();
         int countInGroup = this.readLEB();
         WasmHollowType type = this.reader.readType();
         this.listener.local(localIndex, countInGroup, type);
         localIndex += countInGroup;
      }

      this.reportAddress();
   }
}
