package org.teavm.backend.wasm.vtable;

import org.teavm.model.MethodDescriptor;

public class WasmGCVirtualTableEntry {
   private WasmGCVirtualTable origin;
   private MethodDescriptor method;
   private int index;

   WasmGCVirtualTableEntry(WasmGCVirtualTable origin, MethodDescriptor method, int index) {
      this.origin = origin;
      this.method = method;
      this.index = index;
   }

   public WasmGCVirtualTable getOrigin() {
      return this.origin;
   }

   public MethodDescriptor getMethod() {
      return this.method;
   }

   public int getIndex() {
      return this.index;
   }
}
