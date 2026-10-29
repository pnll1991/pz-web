package org.teavm.backend.wasm.generate;

import java.util.ArrayList;
import java.util.Collection;
import org.teavm.backend.wasm.dwarf.DwarfInfoWriter;
import org.teavm.backend.wasm.dwarf.DwarfPlaceholder;
import org.teavm.backend.wasm.model.WasmCustomSection;
import org.teavm.common.binary.Blob;
import org.teavm.common.binary.Marker;

public class DwarfGenerator {
   private DwarfInfoWriter infoWriter = new DwarfInfoWriter();
   private DwarfPlaceholder endOfSection;
   public final DwarfStrings strings = new DwarfStrings();
   private DwarfStrings lineStrings = new DwarfStrings();
   private DwarfLinesGenerator lines;
   private Marker highPcMarker;

   public DwarfGenerator(SourceFileResolver sourceFileResolver) {
      this.lines = new DwarfLinesGenerator(this.lineStrings, sourceFileResolver);
   }

   public void begin() {
      this.endOfSection = this.infoWriter.placeholder(4);
      this.lines.begin();
      this.emitUnitHeader();
      this.compilationUnit();
   }

   private void emitUnitHeader() {
      this.infoWriter.ref(this.endOfSection, (blob, ptr) -> {
         int size = ptr - blob.ptr() - 4;
         blob.writeInt(size);
      });
      this.infoWriter.writeShort(5);
      this.infoWriter.writeByte(1);
      this.infoWriter.writeByte(4);
      this.infoWriter.writeInt(0);
   }

   private void compilationUnit() {
      this.infoWriter.tag(this.infoWriter.abbreviation(17, true, data -> {
         data.writeLEB(37).writeLEB(14);
         data.writeLEB(19).writeLEB(5);
         data.writeLEB(3).writeLEB(14);
         data.writeLEB(16).writeLEB(23);
         data.writeLEB(17).writeLEB(1);
         data.writeLEB(18).writeLEB(1);
      }));
      this.infoWriter.writeInt(this.strings.stringRef("TeaVM"));
      this.infoWriter.writeShort(4);
      this.infoWriter.writeInt(this.strings.stringRef("classes.wasm"));
      this.infoWriter.writeInt(0);
      this.infoWriter.writeInt(0);
      this.highPcMarker = this.infoWriter.marker();
      this.infoWriter.skip(4);
   }

   public void end() {
      this.closeTag();
      this.infoWriter.mark(this.endOfSection);
      this.lines.end();
   }

   private void closeTag() {
      this.infoWriter.writeByte(0);
   }

   public void setCodeSize(int codeSize) {
      Marker backup = this.infoWriter.marker();
      this.highPcMarker.rewind();
      this.infoWriter.writeInt(codeSize);
      backup.rewind();
   }

   public Collection<? extends WasmCustomSection> createSections() {
      ArrayList<WasmCustomSection> sections = new ArrayList<>();
      Blob abbreviations = new Blob();
      this.infoWriter.buildAbbreviations(abbreviations);
      sections.add(new WasmCustomSection(".debug_abbrev", abbreviations.toArray()));
      Blob info = new Blob();
      this.infoWriter.build(info);
      sections.add(new WasmCustomSection(".debug_info", info.toArray()));
      if (this.strings.blob.size() > 0) {
         sections.add(new WasmCustomSection(".debug_str", this.strings.blob.toArray()));
      }

      if (this.lineStrings.blob.size() > 0) {
         sections.add(new WasmCustomSection(".debug_line_str", this.lineStrings.blob.toArray()));
      }

      sections.add(new WasmCustomSection(".debug_line", this.lines.blob.toArray()));
      return sections;
   }

   public void lineNumber(int address, String fileName, int lineNumber) {
      this.lines.lineNumber(address, fileName, lineNumber);
   }

   public void endLineNumberSequence(int address) {
      this.lines.endLineNumberSequence(address);
   }

   public DwarfInfoWriter getInfoWriter() {
      return this.infoWriter;
   }
}
