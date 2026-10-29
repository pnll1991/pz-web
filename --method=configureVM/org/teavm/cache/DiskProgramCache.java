package org.teavm.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ProgramCache;
import org.teavm.model.ReferenceCache;

public class DiskProgramCache implements ProgramCache {
   private File directory;
   private ProgramIO programIO;
   private Map<MethodReference, DiskProgramCache.Item> cache = new HashMap<>();
   private Set<MethodReference> newMethods = new HashSet<>();

   public DiskProgramCache(File directory, ReferenceCache referenceCache, SymbolTable symbolTable, SymbolTable fileTable, SymbolTable variableTable) {
      this.directory = directory;
      this.programIO = new ProgramIO(referenceCache, symbolTable, fileTable, variableTable);
   }

   @Override
   public Program get(MethodReference method, CacheStatus cacheStatus) {
      DiskProgramCache.Item item = this.cache.get(method);
      if (item == null) {
         item = new DiskProgramCache.Item();
         this.cache.put(method, item);
         File file = this.getMethodFile(method);
         if (file.exists()) {
            try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
               DataInput input = new DataInputStream(stream);
               int depCount = input.readShort();
               boolean dependenciesChanged = false;

               for (int i = 0; i < depCount; i++) {
                  String depClass = input.readUTF();
                  if (cacheStatus.isStaleClass(depClass)) {
                     dependenciesChanged = true;
                     break;
                  }
               }

               if (!dependenciesChanged) {
                  item.program = this.programIO.read(stream);
               }
            } catch (IOException var13) {
            }
         }
      }

      return item.program;
   }

   @Override
   public void store(MethodReference method, Program program, Supplier<String[]> dependencies) {
      DiskProgramCache.Item item = new DiskProgramCache.Item();
      this.cache.put(method, item);
      item.program = program;
      item.dependencies = (String[])dependencies.get().clone();
      this.newMethods.add(method);
   }

   public void flush() throws IOException {
      for (MethodReference method : this.newMethods) {
         DiskProgramCache.Item item = this.cache.get(method);
         File file = this.getMethodFile(method);
         file.getParentFile().mkdirs();

         try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file))) {
            DataOutput output = new DataOutputStream(stream);
            output.writeShort(item.dependencies.length);

            for (String dep : item.dependencies) {
               output.writeUTF(dep);
            }

            this.programIO.write(item.program, stream);
         }
      }
   }

   private File getMethodFile(MethodReference method) {
      File dir = new File(this.directory, method.getClassName().replace('.', '/'));
      return new File(dir, FileNameEncoder.encodeFileName(method.getDescriptor().toString()) + ".teavm-opt");
   }

   static class Item {
      Program program;
      String[] dependencies;
   }
}
