package org.teavm.model.classes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.model.MethodDescriptor;

public class VirtualTable {
   private String className;
   private VirtualTable parent;
   private List<? extends MethodDescriptor> methods;
   private Set<MethodDescriptor> methodSet;
   private Map<MethodDescriptor, VirtualTableEntry> entryMap;
   private boolean hasValidEntries;
   private boolean hasValidEntriesComputed;

   VirtualTable(
      String className,
      VirtualTable parent,
      List<? extends MethodDescriptor> methods,
      Set<MethodDescriptor> methodSet,
      Map<MethodDescriptor, VirtualTableEntry> entryMap
   ) {
      this.className = className;
      this.parent = parent;
      this.methods = methods;
      this.methodSet = methodSet;
      this.entryMap = entryMap;
   }

   public String getClassName() {
      return this.className;
   }

   public VirtualTable getParent() {
      return this.parent;
   }

   public List<? extends MethodDescriptor> getMethods() {
      return this.methods;
   }

   public VirtualTableEntry getEntry(MethodDescriptor method) {
      return this.entryMap.get(method);
   }

   public boolean hasMethod(MethodDescriptor method) {
      return this.methodSet.contains(method);
   }

   public VirtualTable findMethodContainer(MethodDescriptor method) {
      for (VirtualTable vt = this; vt != null; vt = vt.getParent()) {
         if (vt.hasMethod(method)) {
            return vt;
         }
      }

      return null;
   }

   public int size() {
      return this.methods.size() + (this.parent != null ? this.parent.size() : 0);
   }

   public boolean hasValidEntries() {
      return !this.methods.isEmpty();
   }
}
