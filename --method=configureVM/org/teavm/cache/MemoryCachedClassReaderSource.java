package org.teavm.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;

public class MemoryCachedClassReaderSource implements ClassReaderSource, CacheStatus {
   private Map<String, MemoryCachedClassReaderSource.Entry> cache = new HashMap<>();
   private Function<String, ClassReader> provider;
   private ClassIO classIO;
   private final Set<String> freshClasses = new HashSet<>();

   public MemoryCachedClassReaderSource(ReferenceCache referenceCache, SymbolTable symbolTable, SymbolTable fileTable, SymbolTable varTable) {
      this.classIO = new ClassIO(referenceCache, symbolTable, fileTable, varTable);
   }

   public void setProvider(Function<String, ClassReader> provider) {
      this.provider = provider;
   }

   @Override
   public boolean isStaleClass(String className) {
      return !this.freshClasses.contains(className);
   }

   @Override
   public boolean isStaleMethod(MethodReference method) {
      return this.isStaleClass(method.getClassName());
   }

   public void populate(String name) {
      this.getEntry(name);
   }

   @Override
   public ClassReader get(String name) {
      MemoryCachedClassReaderSource.Entry entry = this.getEntry(name);
      if (entry.data == null) {
         return null;
      }

      ClassReader cls = entry.reader;
      if (cls == null) {
         ByteArrayInputStream input = new ByteArrayInputStream(entry.data);

         try {
            cls = this.classIO.readClass(input, name);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

         entry.reader = cls;
      }

      return cls;
   }

   private MemoryCachedClassReaderSource.Entry getEntry(String name) {
      return this.cache.computeIfAbsent(name, className -> {
         ClassReader cls = this.provider != null ? this.provider.apply(className) : null;
         MemoryCachedClassReaderSource.Entry en = new MemoryCachedClassReaderSource.Entry();
         if (cls != null) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            try {
               this.classIO.writeClass(output, cls);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }

            en.data = output.toByteArray();
         }

         return en;
      });
   }

   public void commit() {
      this.freshClasses.addAll(this.cache.keySet());
   }

   public void evict(Collection<? extends String> classes) {
      this.cache.keySet().removeAll(classes);
      this.freshClasses.removeAll(classes);
   }

   public void invalidate() {
      this.cache.clear();
      this.freshClasses.clear();
   }

   static class Entry {
      byte[] data;
      ClassReader reader;
   }
}
