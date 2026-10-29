package org.teavm.backend.wasm.parser;

public class NameSectionParser extends BaseSectionParser {
   private final NameSectionListener listener;

   public NameSectionParser(NameSectionListener listener) {
      this.listener = listener;
   }

   @Override
   protected void parseContent() {
      while (this.reader.ptr < this.reader.data.length) {
         byte sectionType = this.reader.data[this.reader.ptr++];
         int sectionLength = this.readLEB();
         int next = this.reader.ptr + sectionLength;
         switch (sectionType) {
            case 1:
               this.parseNameMapSubsection(this.listener.functions());
               break;
            case 2:
               this.parseIndirectNameMap(this.listener.locals());
            case 3:
            case 5:
            case 6:
            case 8:
            case 9:
            default:
               break;
            case 4:
               this.parseNameMapSubsection(this.listener.types());
               break;
            case 7:
               this.parseNameMapSubsection(this.listener.globals());
               break;
            case 10:
               this.parseIndirectNameMap(this.listener.fields());
         }

         this.reader.ptr = next;
      }
   }

   private void parseNameMapSubsection(NameMapListener subsectionListener) {
      if (subsectionListener != null) {
         this.parseNameMap(subsectionListener);
      }
   }

   private void parseNameMap(NameMapListener subsectionListener) {
      int count = this.readLEB();

      for (int i = 0; i < count; i++) {
         int index = this.readLEB();
         String name = this.readString();
         if (subsectionListener != null) {
            subsectionListener.name(index, name);
         }
      }
   }

   private void parseIndirectNameMap(NameIndirectMapListener subsectionListener) {
      int count = this.readLEB();

      for (int i = 0; i < count; i++) {
         int index = this.readLEB();
         this.parseNameMap(subsectionListener.map(index));
      }
   }
}
