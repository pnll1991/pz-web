package org.teavm.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.ast.RegularMethodNode;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;

public class DiskMethodNodeCache implements MethodNodeCache {
   private final File directory;
   private final AstIO astIO;
   private final Map<MethodReference, DiskMethodNodeCache.Item> cache = new HashMap<>();
   private final Map<MethodReference, DiskMethodNodeCache.AsyncItem> asyncCache = new HashMap<>();
   private final Set<MethodReference> newMethods = new HashSet<>();
   private final Set<MethodReference> newAsyncMethods = new HashSet<>();

   public DiskMethodNodeCache(File directory, ReferenceCache referenceCache, SymbolTable symbolTable, SymbolTable fileTable, SymbolTable variableTable) {
      this.directory = directory;
      this.astIO = new AstIO(referenceCache, symbolTable, fileTable, variableTable);
   }

   @Override
   public AstCacheEntry get(MethodReference methodReference, CacheStatus cacheStatus) {
      DiskMethodNodeCache.Item item = this.cache.get(methodReference);
      if (item == null) {
         item = new DiskMethodNodeCache.Item();
         this.cache.put(methodReference, item);
         File file = this.getMethodFile(methodReference, false);
         if (file.exists()) {
            try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
               VarDataInput input = new VarDataInput(stream);
               if (!this.checkIfDependenciesChanged(input, cacheStatus)) {
                  RegularMethodNode node = this.astIO.read(input, methodReference);
                  ControlFlowEntry[] cfg = this.astIO.readControlFlow(input);
                  item.entry = new AstCacheEntry(node, cfg);
               }
            } catch (IOException var11) {
            }
         }
      }

      return item.entry;
   }

   @Override
   public void store(MethodReference methodReference, AstCacheEntry entry, Supplier<String[]> dependencies) {
      DiskMethodNodeCache.Item item = new DiskMethodNodeCache.Item();
      item.entry = entry;
      item.dependencies = (String[])dependencies.get().clone();
      this.cache.put(methodReference, item);
      this.newMethods.add(methodReference);
   }

   @Override
   public AsyncMethodNode getAsync(MethodReference methodReference, CacheStatus cacheStatus) {
      DiskMethodNodeCache.AsyncItem item = this.asyncCache.get(methodReference);
      if (item == null) {
         item = new DiskMethodNodeCache.AsyncItem();
         this.asyncCache.put(methodReference, item);
         File file = this.getMethodFile(methodReference, true);
         if (file.exists()) {
            try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
               VarDataInput input = new VarDataInput(stream);
               if (!this.checkIfDependenciesChanged(input, cacheStatus)) {
                  item.node = this.astIO.readAsync(input, methodReference);
               }
            } catch (IOException var10) {
            }
         }
      }

      return item.node;
   }

   private boolean checkIfDependenciesChanged(VarDataInput input, CacheStatus cacheStatus) throws IOException {
      int depCount = input.readUnsigned();

      for (int i = 0; i < depCount; i++) {
         String depClass = input.read();
         if (cacheStatus.isStaleClass(depClass)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void storeAsync(MethodReference methodReference, AsyncMethodNode node, Supplier<String[]> depenencies) {
      DiskMethodNodeCache.AsyncItem item = new DiskMethodNodeCache.AsyncItem();
      item.node = node;
      item.dependencies = (String[])depenencies.get().clone();
      this.asyncCache.put(methodReference, item);
      this.newAsyncMethods.add(methodReference);
   }

   public void flush() throws IOException {
      for (MethodReference method : this.newMethods) {
         File file = this.getMethodFile(method, true);
         DiskMethodNodeCache.Item item = this.cache.get(method);

         try (VarDataOutput output = new VarDataOutput(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeUnsigned(item.dependencies.length);

            for (String dependency : item.dependencies) {
               output.write(dependency);
            }

            this.astIO.write(output, item.entry.method);
            this.astIO.write(output, item.entry.cfg);
         }
      }

      for (MethodReference method : this.newAsyncMethods) {
         File file = this.getMethodFile(method, true);
         DiskMethodNodeCache.AsyncItem item = this.asyncCache.get(method);

         try (VarDataOutput output = new VarDataOutput(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeUnsigned(item.dependencies.length);

            for (String dependency : item.dependencies) {
               output.write(dependency);
            }

            this.astIO.writeAsync(output, item.node);
         }
      }
   }

   private File getMethodFile(MethodReference method, boolean async) {
      File dir = new File(this.directory, method.getClassName().replace('.', '/'));
      return new File(dir, FileNameEncoder.encodeFileName(method.getDescriptor().toString()) + ".teavm-ast" + (async ? "-async" : ""));
   }

   private static class AsyncItem {
      AsyncMethodNode node;
      String[] dependencies;
   }

   private static class Item {
      AstCacheEntry entry;
      String[] dependencies;
   }
}
