package org.teavm.backend.wasm.parser;

public class ImportSectionParser extends BaseSectionParser {
   private final ImportSectionListener listener;

   public ImportSectionParser(ImportSectionListener listener) {
      this.listener = listener;
   }

   @Override
   protected void parseContent() {
      int count = this.readLEB();

      for (int i = 0; i < count; i++) {
         this.readEntry();
      }
   }

   private void readEntry() {
      this.reportAddress();
      String module = this.readString();
      String name = this.readString();
      this.listener.startEntry(module, name);
      this.reportAddress();
      byte type = this.reader.data[this.reader.ptr++];
      switch (type) {
         case 0:
            int typeIndex = this.readLEB();
            this.listener.function(typeIndex);
            break;
         case 1:
         default:
            throw new ParseException("Unsupported import type", this.reader.ptr);
         case 2:
            byte limitsType = this.reader.data[this.reader.ptr++];
            if (limitsType == 0) {
               this.listener.memory(this.readLEB(), -1);
            } else {
               this.listener.memory(this.readLEB(), this.readLEB());
            }
            break;
         case 3:
            WasmHollowType valueType = this.reader.readType();
            this.listener.global(valueType, this.reader.readLEB() != 0);
      }

      this.listener.endEntry();
   }
}
