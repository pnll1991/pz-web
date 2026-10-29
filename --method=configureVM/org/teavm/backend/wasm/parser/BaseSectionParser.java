package org.teavm.backend.wasm.parser;

public abstract class BaseSectionParser {
   protected WasmBinaryReader reader;

   public void parse(AddressListener addressListener, byte[] data) {
      this.parse(new WasmBinaryReader(addressListener, data));
   }

   public void parse(WasmBinaryReader reader) {
      this.reader = reader;

      try {
         this.parseContent();
      } finally {
         this.reader = null;
      }
   }

   protected abstract void parseContent();

   protected void reportAddress() {
      this.reader.reportAddress();
   }

   protected int readSignedLEB() {
      return this.reader.readSignedLEB();
   }

   protected int readLEB() {
      return this.reader.readLEB();
   }

   protected long readSignedLongLEB() {
      return this.reader.readSignedLongLEB();
   }

   protected long readLongLEB() {
      return this.reader.readLongLEB();
   }

   protected int readFixedInt() {
      return this.reader.readFixedInt();
   }

   protected long readFixedLong() {
      return this.reader.readFixedLong();
   }

   protected String readString() {
      return this.reader.readString();
   }
}
