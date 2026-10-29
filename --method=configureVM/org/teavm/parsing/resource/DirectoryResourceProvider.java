package org.teavm.parsing.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;

public class DirectoryResourceProvider implements ResourceProvider {
   private File dir;

   public DirectoryResourceProvider(File dir) {
      this.dir = dir;
   }

   @Override
   public Iterator<Resource> getResources(String name) {
      final File file = new File(this.dir, name);
      if (!file.isFile()) {
         return Collections.emptyIterator();
      }

      var resource = new Resource() {
         @Override
         public InputStream open() {
            try {
               return new FileInputStream(file);
            } catch (FileNotFoundException e) {
               throw new UncheckedIOException(e);
            }
         }

         @Override
         public Date getModificationDate() {
            return new Date(file.lastModified());
         }
      };
      return Collections.singleton(resource).iterator();
   }

   @Override
   public void close() {
   }
}
