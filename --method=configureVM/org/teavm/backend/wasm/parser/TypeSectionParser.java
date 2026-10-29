package org.teavm.backend.wasm.parser;

public class TypeSectionParser extends BaseSectionParser {
   private TypeSectionListener listener;
   private int typeIndex;

   public TypeSectionParser(TypeSectionListener listener) {
      this.listener = listener;
   }

   @Override
   protected void parseContent() {
      this.reportAddress();
      int count = this.readLEB();
      this.listener.sectionStart(count);

      for (int i = 0; i < count; i++) {
         this.parseType();
      }

      this.reportAddress();
      this.listener.sectionEnd();
      this.typeIndex = 0;
   }

   private void parseType() {
      if (this.reader.data[this.reader.ptr] == 78) {
         this.parseRecType();
      } else {
         this.parseSubtype();
      }
   }

   private void parseRecType() {
      this.reportAddress();
      this.reader.ptr++;
      int count = this.readLEB();
      this.listener.startRecType(count);

      for (int i = 0; i < count; i++) {
         this.parseSubtype();
      }

      this.listener.endRecType();
   }

   private void parseSubtype() {
      switch (this.reader.data[this.reader.ptr]) {
         case 79:
            this.reportAddress();
            this.reader.ptr++;
            this.parseCompositeType(false, this.readSupertypes());
            break;
         case 80:
            this.reportAddress();
            this.reader.ptr++;
            this.parseCompositeType(true, this.readSupertypes());
            break;
         default:
            this.parseCompositeType(true, new int[0]);
      }
   }

   private void parseCompositeType(boolean open, int[] supertypes) {
      this.reportAddress();
      this.listener.startType(this.typeIndex++, open, supertypes);
      switch (this.reader.data[this.reader.ptr]) {
         case 94:
            this.reportAddress();
            this.reader.ptr++;
            this.listener.startArrayType();
            this.parseField();
            this.listener.endArrayType();
            break;
         case 95:
            this.reportAddress();
            this.reader.ptr++;
            int fieldCount = this.readLEB();
            this.listener.startStructType(fieldCount);

            for (int i = 0; i < fieldCount; i++) {
               this.parseField();
            }

            this.listener.endStructType();
            break;
         case 96:
            this.reportAddress();
            this.reader.ptr++;
            int paramCount = this.readLEB();
            this.listener.funcType(paramCount);

            for (int i = 0; i < paramCount; i++) {
               this.reportAddress();
               this.listener.resultType(this.reader.readType());
            }

            int resultCount = this.readLEB();
            this.listener.funcTypeResults(resultCount);

            for (int i = 0; i < resultCount; i++) {
               this.reportAddress();
               this.listener.resultType(this.reader.readType());
            }

            this.listener.endFuncType();
            break;
         default:
            throw new ParseException("Unknown type declaration", this.reader.ptr);
      }

      this.listener.endType();
   }

   private void parseField() {
      this.reportAddress();
      WasmHollowStorageType type = this.reader.readStorageType();
      boolean mutable = this.reader.data[this.reader.ptr++] != 0;
      this.listener.field(type, mutable);
   }

   private int[] readSupertypes() {
      int count = this.readLEB();
      int[] result = new int[count];

      for (int i = 0; i < count; i++) {
         result[i] = this.readLEB();
      }

      return result;
   }
}
