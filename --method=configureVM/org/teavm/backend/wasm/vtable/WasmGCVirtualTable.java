package org.teavm.backend.wasm.vtable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class WasmGCVirtualTable {
   private WasmGCVirtualTable parent;
   private WasmGCVirtualTable usedParent;
   private boolean usedParentCalculated;
   private boolean used;
   private boolean concrete;
   private String className;
   List<WasmGCVirtualTableEntry> entries;
   MethodReference[] implementors;
   private Map<MethodDescriptor, WasmGCVirtualTableEntry> entryMap;
   boolean fakeInterfaceRepresentative;

   WasmGCVirtualTable(WasmGCVirtualTable parent, String className, boolean used, boolean concrete) {
      this.parent = parent;
      this.className = className;
      this.used = used;
      this.concrete = concrete;
   }

   public WasmGCVirtualTable getParent() {
      return this.parent;
   }

   public WasmGCVirtualTable getUsedParent() {
      if (this.parent == null) {
         return null;
      }

      if (!this.usedParentCalculated) {
         this.usedParentCalculated = true;
         this.usedParent = this.parent.getFirstUsed();
      }

      return this.usedParent;
   }

   public WasmGCVirtualTable getFirstUsed() {
      return this.used ? this : this.getUsedParent();
   }

   public String getClassName() {
      return this.className;
   }

   public List<? extends WasmGCVirtualTableEntry> getEntries() {
      return this.entries;
   }

   public MethodReference implementor(WasmGCVirtualTableEntry entry) {
      return this.implementors[entry.getIndex()];
   }

   public boolean isUsed() {
      return this.used;
   }

   public boolean isConcrete() {
      return this.concrete;
   }

   public boolean isFakeInterfaceRepresentative() {
      return this.fakeInterfaceRepresentative;
   }

   public WasmGCVirtualTable closestNonInterfaceAncestor() {
      WasmGCVirtualTable result = this;

      while (result != null && result.isFakeInterfaceRepresentative()) {
         result = result.parent;
      }

      return result;
   }

   public WasmGCVirtualTableEntry entry(MethodDescriptor method) {
      if (this.entryMap == null) {
         this.entryMap = new HashMap<>();

         for (WasmGCVirtualTableEntry entry : this.entries) {
            this.entryMap.put(entry.getMethod(), entry);
         }
      }

      return this.entryMap.get(method);
   }
}
