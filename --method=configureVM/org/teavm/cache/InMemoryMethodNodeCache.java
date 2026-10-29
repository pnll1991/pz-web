package org.teavm.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.ast.RegularMethodNode;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;

public class InMemoryMethodNodeCache implements MethodNodeCache {
   private Map<MethodReference, InMemoryMethodNodeCache.RegularItem> cache = new HashMap<>();
   private Map<MethodReference, InMemoryMethodNodeCache.RegularItem> newItems = new HashMap<>();
   private Map<MethodReference, InMemoryMethodNodeCache.AsyncItem> asyncCache = new HashMap<>();
   private Map<MethodReference, InMemoryMethodNodeCache.AsyncItem> newAsyncItems = new HashMap<>();
   private AstIO io;

   public InMemoryMethodNodeCache(
      ReferenceCache referenceCache, InMemorySymbolTable symbolTable, InMemorySymbolTable fileSymbolTable, InMemorySymbolTable variableSymbolTable
   ) {
      this.io = new AstIO(referenceCache, symbolTable, fileSymbolTable, variableSymbolTable);
   }

   @Override
   public AstCacheEntry get(MethodReference methodReference, CacheStatus cacheStatus) {
      InMemoryMethodNodeCache.RegularItem item = this.cache.get(methodReference);
      if (item == null) {
         return null;
      }

      if (Arrays.stream(item.dependencies).anyMatch(cacheStatus::isStaleClass)) {
         return null;
      }

      VarDataInput input = new VarDataInput(new ByteArrayInputStream(item.entry));

      try {
         ControlFlowEntry[] cfg = this.io.readControlFlow(input);
         RegularMethodNode ast = this.io.read(input, methodReference);
         return new AstCacheEntry(ast, cfg);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void store(MethodReference methodReference, AstCacheEntry entry, Supplier<String[]> dependencies) {
      this.newItems.put(methodReference, new InMemoryMethodNodeCache.RegularItem(entry, (String[])dependencies.get().clone()));
   }

   @Override
   public AsyncMethodNode getAsync(MethodReference methodReference, CacheStatus cacheStatus) {
      InMemoryMethodNodeCache.AsyncItem item = this.asyncCache.get(methodReference);
      if (item == null) {
         return null;
      }

      if (Arrays.stream(item.dependencies).anyMatch(cacheStatus::isStaleClass)) {
         return null;
      }

      VarDataInput input = new VarDataInput(new ByteArrayInputStream(item.node));

      try {
         return this.io.readAsync(input, methodReference);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void storeAsync(MethodReference methodReference, AsyncMethodNode node, Supplier<String[]> dependencies) {
      this.newAsyncItems.put(methodReference, new InMemoryMethodNodeCache.AsyncItem(node, (String[])dependencies.get().clone()));
   }

   public void commit() {
      this.cache.putAll(this.newItems);
      this.asyncCache.putAll(this.newAsyncItems);
      this.newItems.clear();
      this.newAsyncItems.clear();
   }

   public void discard() {
      this.newItems.clear();
      this.newAsyncItems.clear();
   }

   public void invalidate() {
      this.cache.clear();
      this.newItems.clear();
      this.asyncCache.clear();
      this.newAsyncItems.clear();
   }

   final class AsyncItem {
      final byte[] node;
      final String[] dependencies;

      AsyncItem(AsyncMethodNode node, String[] dependencies) {
         try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            VarDataOutput data = new VarDataOutput(output);
            InMemoryMethodNodeCache.this.io.writeAsync(data, node);
            this.node = output.toByteArray();
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

         this.dependencies = dependencies;
      }
   }

   final class RegularItem {
      final byte[] entry;
      final String[] dependencies;

      RegularItem(AstCacheEntry entry, String[] dependencies) {
         try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            VarDataOutput data = new VarDataOutput(output);
            InMemoryMethodNodeCache.this.io.write(data, entry.cfg);
            InMemoryMethodNodeCache.this.io.write(data, entry.method);
            this.entry = output.toByteArray();
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

         this.dependencies = dependencies;
      }
   }
}
