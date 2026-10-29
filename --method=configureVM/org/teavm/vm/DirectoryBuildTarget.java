package org.teavm.vm;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DirectoryBuildTarget implements BuildTarget {
   private File directory;

   public DirectoryBuildTarget(File directory) {
      this.directory = directory;
   }

   @Override
   public OutputStream createResource(String fileName) throws IOException {
      int index = fileName.lastIndexOf(47);
      if (index >= 0) {
         File dir = new File(this.directory, fileName.substring(0, index));
         if (!dir.exists()) {
            dir.mkdirs();
         }
      }

      return new BufferedOutputStream(new FileOutputStream(new File(this.directory, fileName)), 65536);
   }
}
