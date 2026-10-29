package org.teavm.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.ClassDateProvider;

public class DiskCachedClassReaderSource implements ClassReaderSource, CacheStatus {
   private File directory;
   private ClassHolderSource innerSource;
   private ClassDateProvider classDateProvider;
   private Map<String, DiskCachedClassReaderSource.Item> cache = new LinkedHashMap<>();
   private Set<String> newClasses = new HashSet<>();
   private ClassIO classIO;

   public DiskCachedClassReaderSource(
      File directory,
      ReferenceCache referenceCache,
      SymbolTable symbolTable,
      SymbolTable fileTable,
      SymbolTable variableTable,
      ClassHolderSource innerSource,
      ClassDateProvider classDateProvider
   ) {
      this.directory = directory;
      this.innerSource = innerSource;
      this.classDateProvider = classDateProvider;
      this.classIO = new ClassIO(referenceCache, symbolTable, fileTable, variableTable);
   }

   @Override
   public ClassReader get(String name) {
      return this.getItemFromCache(name).cls;
   }

   @Override
   public boolean isStaleClass(String className) {
      return this.getItemFromCache(className).dirty;
   }

   @Override
   public boolean isStaleMethod(MethodReference method) {
      return this.isStaleClass(method.getClassName());
   }

   private DiskCachedClassReaderSource.Item getItemFromCache(String name) {
      DiskCachedClassReaderSource.Item item = this.cache.get(name);
      if (item == null) {
         item = new DiskCachedClassReaderSource.Item();
         this.cache.put(name, item);
         File classFile = new File(this.directory, name.replace('.', '/') + ".teavm-cls");
         if (classFile.exists()) {
            Date classDate = this.classDateProvider.getModificationDate(name);
            if (classDate != null && classDate.before(new Date(classFile.lastModified()))) {
               try (InputStream input = new BufferedInputStream(new FileInputStream(classFile))) {
                  item.cls = this.classIO.readClass(input, name);
               } catch (IOException e) {
                  item.cls = null;
               }
            }
         }

         if (item.cls == null) {
            item.dirty = true;
            item.cls = this.innerSource.get(name);
            this.newClasses.add(name);
         }
      }

      return item;
   }

   public void flush() throws IOException {
      for (String className : this.newClasses) {
         DiskCachedClassReaderSource.Item item = this.cache.get(className);
         if (item.cls != null) {
            File classFile = new File(this.directory, className.replace('.', '/') + ".teavm-cls");
            classFile.getParentFile().mkdirs();

            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(classFile))) {
               this.classIO.writeClass(output, item.cls);
            }
         }
      }
   }

   private static class Item {
      ClassReader cls;
      boolean dirty;
   }
}
