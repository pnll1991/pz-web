package org.teavm.backend.wasm.generate;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DirectorySourceFileResolver implements SourceFileResolver {
   private List<File> directories;
   private Map<String, DirectorySourceFileResolver.Wrapper> cache = new HashMap<>();

   public DirectorySourceFileResolver(List<File> directories) {
      this.directories = List.copyOf(directories);
   }

   @Override
   public String resolveFile(String file) {
      return this.cache.computeIfAbsent(file, f -> {
         for (File dir : this.directories) {
            File candidate = new File(dir, f);
            if (candidate.isFile()) {
               return new DirectorySourceFileResolver.Wrapper(candidate.getAbsolutePath());
            }
         }

         return new DirectorySourceFileResolver.Wrapper(null);
      }).value;
   }

   private static class Wrapper {
      final String value;

      Wrapper(String value) {
         this.value = value;
      }
   }
}
