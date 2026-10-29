package org.teavm.parsing.resource;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public interface ResourceProvider extends AutoCloseable {
   ResourceProvider EMPTY = new ResourceProvider() {
      @Override
      public Iterator<Resource> getResources(String name) {
         return Collections.emptyIterator();
      }

      @Override
      public void close() {
      }
   };

   default Resource getResource(String name) {
      Iterator<Resource> iter = this.getResources(name);
      return iter.hasNext() ? iter.next() : null;
   }

   Iterator<Resource> getResources(String var1);

   @Override
   void close();

   static ResourceProvider ofClassPath(List<File> entries) {
      ArrayList<ResourceProvider> providers = new ArrayList<>();

      for (File entry : entries) {
         if (entry.isFile()) {
            providers.add(new ZipFileResourceProvider(entry));
         } else if (entry.isDirectory()) {
            providers.add(new DirectoryResourceProvider(entry));
         }
      }

      if (providers.isEmpty()) {
         return EMPTY;
      } else {
         return providers.size() == 1 ? providers.get(0) : new CompositeResourceProvider(providers.toArray(new ResourceProvider[0]));
      }
   }
}
