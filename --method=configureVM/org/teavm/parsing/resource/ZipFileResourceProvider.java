package org.teavm.parsing.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipFileResourceProvider implements ResourceProvider {
   private File file;
   private ZipFile zipFile;
   private Map<String, Optional<ZipEntry>> entryCache = new HashMap<>();

   public ZipFileResourceProvider(File file) {
      this.file = Objects.requireNonNull(file);

      try {
         this.zipFile = new ZipFile(file);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public ZipFileResourceProvider(String fileName) {
      this(new File(fileName));
   }

   @Override
   public Iterator<Resource> getResources(String name) {
      final ZipEntry entry = this.getEntry(name);
      if (entry == null) {
         return Collections.emptyIterator();
      }

      var resource = new Resource() {
         @Override
         public InputStream open() {
            try {
               return ZipFileResourceProvider.this.zipFile.getInputStream(entry);
            } catch (IOException e) {
               throw new UncheckedIOException(e);
            }
         }

         @Override
         public Date getModificationDate() {
            long date = entry.getTime();
            if (date < 0L) {
               date = ZipFileResourceProvider.this.file.lastModified();
            }

            return new Date(date);
         }
      };
      return Collections.singleton(resource).iterator();
   }

   private ZipEntry getEntry(String name) {
      return this.entryCache.computeIfAbsent(name, n -> Optional.ofNullable(this.zipFile.getEntry(name))).orElse(null);
   }

   @Override
   public void close() {
      try {
         this.zipFile.close();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }
}
