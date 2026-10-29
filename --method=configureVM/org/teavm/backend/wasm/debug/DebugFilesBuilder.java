package org.teavm.backend.wasm.debug;

import java.util.Objects;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public class DebugFilesBuilder extends DebugSectionBuilder implements DebugFiles {
   private DebugStrings strings;
   private ObjectIntMap<DebugFilesBuilder.FileData> fileMap = new ObjectIntHashMap();

   public DebugFilesBuilder(DebugStrings strings) {
      super("teavm_file");
      this.strings = strings;
   }

   @Override
   public int filePtr(String fileName) {
      int index = 0;
      int current = 0;

      while (true) {
         int next = fileName.indexOf(47, index);
         if (next < 0) {
            return this.filePtr(current, fileName.substring(index));
         }

         String dirName = fileName.substring(index, next);
         current = this.filePtr(current, dirName);
         index = next + 1;
      }
   }

   private int filePtr(int parent, String fileName) {
      DebugFilesBuilder.FileData data = new DebugFilesBuilder.FileData(parent, fileName);
      int ptr = this.fileMap.getOrDefault(data, 0);
      if (ptr == 0) {
         ptr = this.fileMap.size() + 1;
         this.fileMap.put(data, ptr);
         this.blob.writeLEB(parent);
         int extensionIndex = fileName.lastIndexOf(46);
         if (extensionIndex < 0) {
            this.blob.writeLEB(this.strings.stringPtr(fileName) << 1);
         } else {
            this.blob.writeLEB(1 | this.strings.stringPtr(fileName.substring(0, extensionIndex)) << 1);
            this.blob.writeLEB(this.strings.stringPtr(fileName.substring(extensionIndex + 1)));
         }
      }

      return ptr;
   }

   private static class FileData {
      private final int parent;
      private final String name;

      FileData(int parent, String name) {
         this.parent = parent;
         this.name = name;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            DebugFilesBuilder.FileData fileData = (DebugFilesBuilder.FileData)o;
            return this.parent == fileData.parent && this.name.equals(fileData.name);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.parent, this.name);
      }
   }
}
