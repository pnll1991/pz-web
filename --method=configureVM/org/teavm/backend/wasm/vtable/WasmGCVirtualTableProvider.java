package org.teavm.backend.wasm.vtable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;

public class WasmGCVirtualTableProvider {
   private Map<String, WasmGCVirtualTable> virtualTables;

   public WasmGCVirtualTableProvider(ListableClassReaderSource classes, Collection<MethodReference> methodsAtCallSites, Predicate<MethodReference> isVirtual) {
      WasmGCVirtualTableBuilder builder = new WasmGCVirtualTableBuilder();
      builder.classes = classes;
      builder.methodsAtCallSites = methodsAtCallSites;
      builder.isVirtual = isVirtual;
      builder.build();
      this.virtualTables = builder.result;
   }

   public WasmGCVirtualTable lookup(String name) {
      return this.virtualTables.get(name);
   }
}
