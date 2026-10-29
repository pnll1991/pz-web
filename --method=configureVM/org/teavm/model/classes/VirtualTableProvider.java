package org.teavm.model.classes;

import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.model.MethodReference;

public class VirtualTableProvider {
   Map<String, VirtualTable> virtualTables = new LinkedHashMap<>();

   VirtualTableProvider() {
   }

   public VirtualTableEntry lookup(MethodReference method) {
      VirtualTable vtable = this.virtualTables.get(method.getClassName());
      return vtable == null ? null : vtable.getEntry(method.getDescriptor());
   }

   public VirtualTable lookup(String className) {
      return this.virtualTables.get(className);
   }
}
