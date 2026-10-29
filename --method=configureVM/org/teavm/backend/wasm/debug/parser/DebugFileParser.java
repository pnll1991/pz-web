package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import org.teavm.backend.wasm.debug.info.FileInfo;

public class DebugFileParser extends DebugSectionParser {
   private DebugStringParser strings;
   private DebugFileParser.FileInfoImpl[] files;

   public DebugFileParser(DebugStringParser strings) {
      super("teavm_file", strings);
      this.strings = strings;
   }

   public FileInfo getFile(int index) {
      return index > 0 ? this.files[index - 1] : null;
   }

   @Override
   protected void doParse() {
      ArrayList<DebugFileParser.FileBuilder> builders = new ArrayList<>();

      while (this.ptr < this.data.length) {
         int parent = this.readLEB();
         int nameIndex = this.readLEB();
         String name = (nameIndex & 1) == 0
            ? this.strings.getString(nameIndex >>> 1)
            : this.strings.getString(nameIndex >>> 1) + "." + this.strings.getString(this.readLEB());
         builders.add(new DebugFileParser.FileBuilder(parent, name));
      }

      this.files = new DebugFileParser.FileInfoImpl[builders.size()];

      for (int i = 0; i < this.files.length; i++) {
         this.files[i] = new DebugFileParser.FileInfoImpl(builders.get(i).name);
      }

      for (int i = 0; i < this.files.length; i++) {
         this.files[i].parent = this.getFile(builders.get(i).parent);
      }
   }

   private static class FileBuilder {
      int parent;
      String name;

      FileBuilder(int parent, String name) {
         this.parent = parent;
         this.name = name;
      }
   }

   private static class FileInfoImpl extends FileInfo {
      private FileInfo parent;
      private final String name;

      private FileInfoImpl(String name) {
         this.name = name;
      }

      @Override
      public FileInfo parent() {
         return this.parent;
      }

      @Override
      public String name() {
         return this.name;
      }
   }
}
