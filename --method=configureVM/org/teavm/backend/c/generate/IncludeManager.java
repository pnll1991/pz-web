package org.teavm.backend.c.generate;

import java.util.HashSet;
import java.util.Set;
import org.teavm.model.ValueType;

public abstract class IncludeManager {
   private FileNameProvider fileNames;
   private String currentFileName;
   private Set<String> includedFiles = new HashSet<>();
   private Set<String> includedClasses = new HashSet<>();
   private Set<ValueType> includedTypes = new HashSet<>();

   public IncludeManager(FileNameProvider fileNames) {
      this.fileNames = fileNames;
   }

   public void init(String currentFileName) {
      this.currentFileName = currentFileName;
      this.includedFiles.clear();
      this.includedClasses.clear();
      this.includedTypes.clear();
   }

   public abstract void addInclude(String var1);

   public String relativeIncludeString(String fileName) {
      int commonIndex = 0;

      while (true) {
         int next = fileName.indexOf(47, commonIndex);
         if (next < 0
            || next > this.currentFileName.length()
            || !this.currentFileName.regionMatches(commonIndex, fileName, commonIndex, next - commonIndex + 1)) {
            StringBuilder sb = new StringBuilder("\"");
            int index = commonIndex;

            while (true) {
               int nextx = this.currentFileName.indexOf(47, index);
               if (nextx < 0) {
                  return sb.append(fileName.substring(commonIndex)).append("\"").toString();
               }

               index = nextx + 1;
               sb.append("../");
            }
         }

         commonIndex = next + 1;
      }
   }

   public void includeClass(String className) {
      if (this.includedClasses.add(className)) {
         this.includePath(this.fileNames.fileName(className) + ".h");
      }
   }

   public void includeType(ValueType type) {
      if (this.includedTypes.add(type)) {
         this.includePath(this.fileNames.fileName(type) + ".h");
      }
   }

   public void includePath(String fileName) {
      if (this.includedFiles.add(fileName)) {
         this.addInclude(this.relativeIncludeString(fileName));
      }
   }
}
