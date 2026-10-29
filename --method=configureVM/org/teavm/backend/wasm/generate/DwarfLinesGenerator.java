package org.teavm.backend.wasm.generate;

import java.util.HashMap;
import java.util.Map;
import org.teavm.common.binary.Blob;
import org.teavm.common.binary.Marker;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

class DwarfLinesGenerator {
   private static final int MIN_INSN_LEN = 1;
   private static final int LINE_BASE = -3;
   private static final int LINE_RANGE = 8;
   private static final int OPCODE_BASE = 13;
   Blob blob = new Blob();
   private DwarfStrings strings;
   private SourceFileResolver sourceFileResolver;
   private Blob instructionsBlob = new Blob();
   private Marker unitLengthMarker;
   private Marker headerLengthMarker;
   private Blob filesBlob = new Blob();
   private ObjectIntMap<String> fileIndexes = new ObjectIntHashMap();
   private Blob dirsBlob = new Blob();
   private ObjectIntMap<String> dirIndexes = new ObjectIntHashMap();
   private int address;
   private int file = 1;
   private int line = 1;
   private boolean sequenceStarted;
   private Map<String, String> resolvedFileMap = new HashMap<>();

   DwarfLinesGenerator(DwarfStrings strings, SourceFileResolver sourceFileResolver) {
      this.strings = strings;
      this.sourceFileResolver = sourceFileResolver;
   }

   void begin() {
      this.emitLinesHeader();
   }

   private void emitLinesHeader() {
      this.unitLengthMarker = this.blob.marker();
      this.blob.skip(4);
      this.blob.writeShort(5);
      this.blob.writeByte(4);
      this.blob.writeByte(0);
      this.headerLengthMarker = this.blob.marker();
      this.blob.skip(4);
      this.blob.writeByte(1);
      this.blob.writeByte(1);
      this.blob.writeByte(1);
      this.blob.writeByte(-3).writeByte(8).writeByte(13);
      this.blob.writeByte(0);
      this.blob.writeByte(1);
      this.blob.writeByte(1);
      this.blob.writeByte(1);
      this.blob.writeByte(1);
      this.blob.writeByte(0);
      this.blob.writeByte(0);
      this.blob.writeByte(0);
      this.blob.writeByte(1);
      this.blob.writeByte(0);
      this.blob.writeByte(0);
      this.blob.writeByte(1);
   }

   void end() {
      this.emitDirsAndFiles();
      this.finishHeader();
      this.instructionsBlob.newReader(this.blob.writer()).readRemaining();
      int length = this.blob.ptr() - this.unitLengthMarker.ptr() - 4;
      this.unitLengthMarker.rewind();
      this.blob.writeInt(length);
   }

   private void emitDirsAndFiles() {
      this.blob.writeByte(1);
      this.blob.writeByte(1);
      this.blob.writeByte(31);
      this.blob.writeLEB(this.dirIndexes.size());
      this.dirsBlob.newReader(this.blob.writer()).readRemaining();
      this.blob.writeByte(2);
      this.blob.writeByte(2);
      this.blob.writeByte(5);
      this.blob.writeByte(1);
      this.blob.writeByte(31);
      this.blob.writeLEB(this.fileIndexes.size());
      this.filesBlob.newReader(this.blob.writer()).readRemaining();
   }

   private void finishHeader() {
      Marker marker = this.blob.marker();
      int headerLength = this.blob.ptr() - this.headerLengthMarker.ptr() - 4;
      this.headerLengthMarker.rewind();
      this.blob.writeInt(headerLength);
      marker.rewind();
   }

   private int fileRef(String path) {
      path = this.resolvePath(path);
      int ref = this.fileIndexes.getOrDefault(path, -1);
      if (ref < 0) {
         int nameIndex = path.lastIndexOf(47) + 1;
         String name = path.substring(nameIndex);
         String dir = path.substring(0, Math.max(0, nameIndex - 1));
         int dirPtr = this.dirRef(dir);
         ref = this.fileIndexes.size();
         this.fileIndexes.put(path, ref);
         this.filesBlob.writeShort(dirPtr);
         this.filesBlob.writeInt(this.strings.stringRef(name));
      }

      return ref;
   }

   private String resolvePath(String path) {
      return this.sourceFileResolver == null ? path : this.resolvedFileMap.computeIfAbsent(path, p -> {
         String result = this.sourceFileResolver.resolveFile(p);
         return result != null ? result : p;
      });
   }

   private int dirRef(String path) {
      int ref = this.dirIndexes.getOrDefault(path, -1);
      if (ref < 0) {
         ref = this.dirIndexes.size();
         this.dirIndexes.put(path, ref);
         this.dirsBlob.writeInt(this.strings.stringRef(path));
      }

      return ref;
   }

   void lineNumber(int address, String file, int line) {
      boolean changed = false;
      int fileRef = this.fileRef(file);
      if (fileRef != this.file) {
         this.file = fileRef;
         this.instructionsBlob.writeByte(4);
         this.instructionsBlob.writeLEB(fileRef);
         changed = true;
      }

      if (line != this.line || this.address != address) {
         changed = this.advanceTo(address, line);
      }

      if (changed) {
         this.instructionsBlob.writeByte(1);
      }

      this.sequenceStarted = true;
   }

   void endLineNumberSequence(int address) {
      if (this.sequenceStarted) {
         if (this.address != address) {
            this.advanceTo(address, this.line);
         }

         this.instructionsBlob.writeByte(0);
         this.instructionsBlob.writeByte(1);
         this.instructionsBlob.writeByte(1);
         this.line = 1;
         this.file = 1;
         this.address = 0;
         this.sequenceStarted = false;
      }
   }

   private boolean advanceTo(int address, int line) {
      int lineIncrement = line - this.line;
      int addressIncrement = address - this.address;
      boolean result = !this.tryEmitSpecial(lineIncrement, addressIncrement);
      if (result) {
         if (lineIncrement != 0) {
            this.instructionsBlob.writeByte(3);
            this.instructionsBlob.writeSLEB(lineIncrement);
         }

         if (addressIncrement != 0) {
            this.instructionsBlob.writeByte(2);
            this.instructionsBlob.writeLEB(addressIncrement);
         }
      }

      this.line = line;
      this.address = address;
      return result;
   }

   private boolean tryEmitSpecial(int lineIncrement, int addressIncrement) {
      if (lineIncrement >= -3 && lineIncrement < 5) {
         int opcode = lineIncrement - -3 + 8 * addressIncrement + 13;
         if (opcode > 13 && opcode <= 255) {
            this.instructionsBlob.writeByte(opcode);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
