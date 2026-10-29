package org.teavm.backend.wasm.debug;

import java.util.List;
import java.util.function.Function;
import org.teavm.backend.wasm.model.WasmCustomSection;
import org.teavm.backend.wasm.parser.AddressListener;
import org.teavm.backend.wasm.parser.WasmBinaryReader;
import org.teavm.backend.wasm.render.WasmBinaryWriter;

public final class ExternalDebugFile {
   private static final int SIGNATURE = 1734501492;

   private ExternalDebugFile() {
   }

   public static byte[] write(List<WasmCustomSection> sections) {
      if (sections.isEmpty()) {
         return null;
      }

      WasmBinaryWriter writer = new WasmBinaryWriter();
      writer.writeInt32(1734501492);
      writer.writeLEB(1);

      for (WasmCustomSection section : sections) {
         byte[] data = section.getData();
         writer.writeAsciiString(section.getName());
         writer.writeLEB(data.length);
         writer.writeBytes(data);
      }

      return writer.getData();
   }

   public static boolean read(byte[] data, Function<String, SectionDataConsumer> consumer) {
      if (data.length < 4) {
         return false;
      }

      WasmBinaryReader reader = new WasmBinaryReader(AddressListener.EMPTY, data);
      int header = reader.readInt32();
      if (header != 1734501492) {
         return false;
      }

      int version = reader.readLEB();
      if (version != 1) {
         return false;
      }

      while (reader.ptr < data.length) {
         String name = reader.readString();
         int length = reader.readLEB();
         SectionDataConsumer sectionConsumer = consumer.apply(name);
         if (sectionConsumer != null) {
            sectionConsumer.accept(data, reader.ptr, reader.ptr + length);
         }

         reader.ptr += length;
      }

      return true;
   }
}
