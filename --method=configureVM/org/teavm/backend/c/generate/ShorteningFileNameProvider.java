package org.teavm.backend.c.generate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.model.ValueType;

public class ShorteningFileNameProvider implements FileNameProvider {
   private static final int MAX_NAME_LENGTH = 40;
   private final FileNameProvider nameProvider;
   private final Map<String, String> names = new HashMap<>();
   private final Set<String> usedNames = new HashSet<>();

   public ShorteningFileNameProvider(FileNameProvider nameProvider) {
      this.nameProvider = nameProvider;
   }

   @Override
   public String fileName(String className) {
      return this.process(this.nameProvider.fileName(className));
   }

   @Override
   public String fileName(ValueType type) {
      return this.process(this.nameProvider.fileName(type));
   }

   @Override
   public String escapeName(String name) {
      return this.process(this.nameProvider.escapeName(name));
   }

   private String process(String name) {
      return this.names.computeIfAbsent(name, n -> this.unique(shorten(n)));
   }

   private String unique(String name) {
      if (this.usedNames.add(name)) {
         return name;
      }

      int suffix = 1;

      String candidate;
      do {
         candidate = name + "_" + suffix++;
      } while (!this.usedNames.add(candidate));

      return candidate;
   }

   private static String shorten(String name) {
      StringBuilder sb = new StringBuilder();
      int index = 0;

      while (true) {
         int next = name.indexOf(47, index);
         if (next < 0) {
            return sb.append(shortenBaseName(name.substring(index))).toString();
         }

         if (next > index) {
            sb.append(name.charAt(index));
         }

         sb.append('/');
         index = next + 1;
      }
   }

   private static String shortenBaseName(String baseName) {
      return baseName.length() <= 40 ? baseName : baseName.substring(0, 40);
   }
}
