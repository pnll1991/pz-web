package org.teavm.vm;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MemoryBuildTarget implements BuildTarget {
   private Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
   private Set<? extends String> names = Collections.unmodifiableSet(this.data.keySet());

   public Set<? extends String> getNames() {
      return this.names;
   }

   public byte[] getContent(String name) {
      ByteArrayOutputStream stream = this.data.get(name);
      return stream != null ? stream.toByteArray() : null;
   }

   public void clear() {
      this.data.clear();
   }

   @Override
   public OutputStream createResource(String fileName) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      this.data.put(fileName, stream);
      return stream;
   }

   public OutputStream appendToResource(String fileName) {
      return this.data.computeIfAbsent(fileName, k -> new ByteArrayOutputStream());
   }
}
