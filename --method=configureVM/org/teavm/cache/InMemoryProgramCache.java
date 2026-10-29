package org.teavm.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ProgramCache;
import org.teavm.model.ReferenceCache;

public class InMemoryProgramCache implements ProgramCache {
   private Map<MethodReference, InMemoryProgramCache.Item> cache = new HashMap<>();
   private Map<MethodReference, InMemoryProgramCache.Item> newItems = new HashMap<>();
   private ProgramIO io;

   public InMemoryProgramCache(
      ReferenceCache referenceCache, InMemorySymbolTable symbolTable, InMemorySymbolTable fileSymbolTable, InMemorySymbolTable variableSymbolTable
   ) {
      this.io = new ProgramIO(referenceCache, symbolTable, fileSymbolTable, variableSymbolTable);
   }

   @Override
   public Program get(MethodReference method, CacheStatus cacheStatus) {
      InMemoryProgramCache.Item item = this.cache.get(method);
      if (item == null) {
         return null;
      }

      if (Arrays.stream(item.dependencies).anyMatch(cacheStatus::isStaleClass)) {
         return null;
      }

      try {
         ByteArrayInputStream input = new ByteArrayInputStream(item.program);
         return this.io.read(input);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void store(MethodReference method, Program program, Supplier<String[]> dependencies) {
      try {
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         this.io.write(program, output);
         this.newItems.put(method, new InMemoryProgramCache.Item(output.toByteArray(), (String[])dependencies.get().clone()));
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public void commit() {
      this.cache.putAll(this.newItems);
      this.newItems.clear();
   }

   public int getPendingItemsCount() {
      return this.newItems.size();
   }

   public void discard() {
      this.newItems.clear();
   }

   public void invalidate() {
      this.cache.clear();
      this.newItems.clear();
   }

   static final class Item {
      final byte[] program;
      final String[] dependencies;

      Item(byte[] program, String[] dependencies) {
         this.program = program;
         this.dependencies = dependencies;
      }
   }
}
