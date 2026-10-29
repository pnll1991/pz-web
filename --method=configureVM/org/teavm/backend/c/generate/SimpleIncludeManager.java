package org.teavm.backend.c.generate;

import java.util.HashSet;
import java.util.Set;

public class SimpleIncludeManager extends IncludeManager {
   private Set<String> includes = new HashSet<>();
   private CodeWriter writer;
   private CodeWriter emptyLine;

   public SimpleIncludeManager(FileNameProvider fileNames, CodeWriter writer) {
      super(fileNames);
      this.writer = writer.fragment();
      this.emptyLine = writer.fragment();
   }

   @Override
   public void addInclude(String include) {
      if (this.includes.add(include)) {
         if (this.includes.size() == 1) {
            this.emptyLine.println();
         }

         this.writer.println("#include " + include);
      }
   }
}
