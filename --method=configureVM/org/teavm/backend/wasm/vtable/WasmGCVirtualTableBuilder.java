package org.teavm.backend.wasm.vtable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.teavm.common.LCATree;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.cursors.ObjectIntCursor;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;

class WasmGCVirtualTableBuilder {
   private static final MethodReference CLONE_METHOD = new MethodReference(Object.class, "clone", Object.class);
   ListableClassReaderSource classes;
   Collection<MethodReference> methodsAtCallSites;
   Predicate<MethodReference> isVirtual;
   private Map<String, Set<MethodDescriptor>> groupedMethodsAtCallSites = new HashMap<>();
   private List<WasmGCVirtualTableBuilder.Table> tables = new ArrayList<>();
   private Map<String, WasmGCVirtualTableBuilder.Table> tableMap = new HashMap<>();
   private LCATree lcaTree;
   Map<String, WasmGCVirtualTable> result = new HashMap<>();

   void build() {
      this.initTables();
      this.buildLCA();
      this.initInterfaceTables();
      this.fillInterfaceImplementors();
      this.mergeTrivialInterfaces();
      this.liftInterfaces();
      this.buildInterfacesHierarchy();
      this.groupMethodsFromCallSites();
      this.moveClassesToMergedTables();
      this.fillTables();
      this.buildResult();
   }

   private void initTables() {
      for (String className : this.classes.getClassNames()) {
         this.initTable(className);
      }
   }

   private void initTable(String className) {
      ClassReader cls = this.classes.get(className);
      if (!cls.hasModifier(ElementModifier.INTERFACE) && !this.tableMap.containsKey(className)) {
         if (cls.getParent() != null) {
            this.initTable(cls.getParent());
         }

         WasmGCVirtualTableBuilder.Table table = new WasmGCVirtualTableBuilder.Table(cls, this.tables.size());
         if (cls.getParent() != null) {
            table.parent = this.tableMap.get(cls.getParent());
         }

         this.tables.add(table);
         this.tableMap.put(className, table);
      }
   }

   private void buildLCA() {
      this.lcaTree = new LCATree(this.tables.size() + 1);

      for (int i = 0; i < this.tables.size(); i++) {
         WasmGCVirtualTableBuilder.Table table = this.tables.get(i);
         WasmGCVirtualTableBuilder.Table parentTable = table.cls.getParent() != null ? this.tableMap.get(table.cls.getParent()) : null;
         this.lcaTree.addNode(parentTable != null ? parentTable.index + 1 : 0);
      }
   }

   private void initInterfaceTables() {
      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (cls.hasModifier(ElementModifier.INTERFACE)) {
            WasmGCVirtualTableBuilder.Table table = new WasmGCVirtualTableBuilder.Table(cls, this.tables.size());
            this.tables.add(table);
            this.tableMap.put(className, table);
         }
      }
   }

   private void fillInterfaceImplementors() {
      for (WasmGCVirtualTableBuilder.Table table : this.tables) {
         if (!table.cls.hasModifier(ElementModifier.INTERFACE)) {
            HashSet<String> visited = new HashSet<>();

            while (true) {
               for (String itfName : table.cls.getInterfaces()) {
                  this.addImplementorToInterface(itfName, table, visited);
               }

               table = table.cls.getParent() != null ? this.tableMap.get(table.cls.getParent()) : null;
               if (table == null) {
                  break;
               }
            }
         }
      }
   }

   private void addImplementorToInterface(String interfaceName, WasmGCVirtualTableBuilder.Table newImplementor, Set<String> visited) {
      if (visited.add(interfaceName)) {
         WasmGCVirtualTableBuilder.Table itf = this.tableMap.get(interfaceName);
         if (itf != null) {
            if (!itf.commonImplementorFilled) {
               itf.commonImplementorFilled = true;
               itf.commonImplementor = newImplementor.parent;
            } else if (itf.commonImplementor != null) {
               int lcaIndex = this.lcaTree.lcaOf(newImplementor.index + 1, itf.commonImplementor.index + 1);
               if (lcaIndex > 0) {
                  itf.commonImplementor = this.tables.get(lcaIndex - 1);
                  if (itf.commonImplementor == newImplementor) {
                     itf.commonImplementor = newImplementor.parent;
                  }
               } else {
                  itf.commonImplementor = null;
               }
            }

            ClassReader cls = this.classes.get(interfaceName);
            if (cls != null) {
               for (String superInterface : cls.getInterfaces()) {
                  this.addImplementorToInterface(superInterface, newImplementor, visited);
               }
            }
         }
      }
   }

   private void mergeTrivialInterfaces() {
      for (WasmGCVirtualTableBuilder.Table table : this.tables) {
         if (table.cls.hasModifier(ElementModifier.INTERFACE)
            && table.cls.getInterfaces().isEmpty()
            && table.cls.getMethods().isEmpty()
            && table.commonImplementor != null) {
            table.interfaceMergedIntoClass = true;
            table.commonImplementor.merge(table);
         }
      }
   }

   private void liftInterfaces() {
      for (WasmGCVirtualTableBuilder.Table table : this.tables) {
         if (!table.cls.hasModifier(ElementModifier.INTERFACE) && !table.cls.getInterfaces().isEmpty()) {
            LinkedHashSet<WasmGCVirtualTableBuilder.Table> accumulatedInterfaces = new LinkedHashSet<>();

            for (String itfName : table.cls.getInterfaces()) {
               WasmGCVirtualTableBuilder.Table itf = this.tableMap.get(itfName);
               if (itf != null && !itf.interfaceMergedIntoClass) {
                  accumulatedInterfaces.add(itf);
               }
            }

            while (table != null) {
               if (table.liftedInterfaces == null) {
                  table.liftedInterfaces = new LinkedHashSet<>();
               } else {
                  accumulatedInterfaces.removeAll(table.liftedInterfaces);
               }

               if (accumulatedInterfaces.isEmpty()) {
                  break;
               }

               table.liftedInterfaces.addAll(accumulatedInterfaces);
               WasmGCVirtualTableBuilder.Table parent = table.parent;
               LinkedHashSet<WasmGCVirtualTableBuilder.Table> accumulatedInterfacesToAdd = new LinkedHashSet<>();
               Iterator<WasmGCVirtualTableBuilder.Table> iter = accumulatedInterfaces.iterator();

               while (iter.hasNext()) {
                  WasmGCVirtualTableBuilder.Table itf = iter.next();
                  if (itf.commonImplementor == parent) {
                     iter.remove();
                     this.addNextInterfaces(itf, parent, new HashSet<>(), accumulatedInterfacesToAdd);
                  }
               }

               accumulatedInterfaces.addAll(accumulatedInterfacesToAdd);
               if (accumulatedInterfaces.isEmpty()) {
                  break;
               }

               table = parent;
            }
         }
      }

      for (WasmGCVirtualTableBuilder.Table table : this.tables) {
         if (table.liftedInterfaces != null) {
            table.liftedInterfaces.removeIf(itfx -> itfx.commonImplementor != table.parent);
            if (table.liftedInterfaces.isEmpty()) {
               table.liftedInterfaces = null;
            }
         }
      }
   }

   private void addNextInterfaces(
      WasmGCVirtualTableBuilder.Table itf,
      WasmGCVirtualTableBuilder.Table parent,
      Set<WasmGCVirtualTableBuilder.Table> visited,
      Set<WasmGCVirtualTableBuilder.Table> result
   ) {
      if (visited.add(itf)) {
         if (itf.commonImplementor != parent) {
            result.add(itf);
         } else {
            for (String superItfName : itf.cls.getInterfaces()) {
               WasmGCVirtualTableBuilder.Table superItf = this.tableMap.get(superItfName);
               if (superItf != null) {
                  this.addNextInterfaces(superItf, parent, visited, result);
               }
            }
         }
      }
   }

   private void buildInterfacesHierarchy() {
      for (WasmGCVirtualTableBuilder.Table table : this.tables) {
         if (table.cls.hasModifier(ElementModifier.INTERFACE)) {
            this.setUpInterfaceInHierarchy(table, table.commonImplementor);
         } else if (table.liftedInterfaces != null) {
            this.setUpInterfaceInHierarchy(table, table.parent);
         }
      }
   }

   private void setUpInterfaceInHierarchy(WasmGCVirtualTableBuilder.Table table, WasmGCVirtualTableBuilder.Table parent) {
      if (!table.visited) {
         table.visited = true;
         LinkedHashSet<WasmGCVirtualTableBuilder.Table> interfaces = new LinkedHashSet<>();
         if (table.liftedInterfaces != null) {
            for (WasmGCVirtualTableBuilder.Table itf : table.liftedInterfaces) {
               itf = itf.resolve();
               if (itf.commonImplementor == parent && !itf.interfaceMergedIntoClass) {
                  this.setUpInterfaceInHierarchy(itf, parent);
                  interfaces.add(itf.resolve());
               }
            }
         } else {
            for (String itfName : table.cls.getInterfaces()) {
               WasmGCVirtualTableBuilder.Table itf = this.tableMap.get(itfName);
               if (itf != null) {
                  itf = itf.resolve();
                  if (itf.commonImplementor == parent && !itf.interfaceMergedIntoClass) {
                     this.setUpInterfaceInHierarchy(itf, parent);
                     interfaces.add(itf.resolve());
                  }
               }
            }
         }

         if (interfaces.isEmpty()) {
            table.parent = parent;
            table.depth = 0;
         } else {
            int maxDepth = 0;

            for (WasmGCVirtualTableBuilder.Table itf : interfaces) {
               maxDepth = Math.max(itf.depth, maxDepth);
            }

            WasmGCVirtualTableBuilder.Table singleExample = null;

            for (int i = 0; i <= maxDepth; i++) {
               int level = i;
               List<WasmGCVirtualTableBuilder.Table> interfacesAtLevel = interfaces.stream()
                  .map(itf -> itf.atDepth(level))
                  .filter(Objects::nonNull)
                  .map(WasmGCVirtualTableBuilder.Table::resolve)
                  .distinct()
                  .collect(Collectors.toList());
               singleExample = interfacesAtLevel.get(0);

               for (int j = 1; j < interfacesAtLevel.size(); j++) {
                  singleExample.merge(interfacesAtLevel.get(j));
               }
            }

            table.parent = singleExample;
            table.depth = singleExample.depth + 1;
         }
      }
   }

   private void groupMethodsFromCallSites() {
      for (MethodReference methodRef : this.methodsAtCallSites) {
         String className = this.mapClassName(methodRef.getClassName());
         Set<MethodDescriptor> group = this.groupedMethodsAtCallSites.computeIfAbsent(className, k -> new LinkedHashSet<>());
         group.add(methodRef.getDescriptor());
      }
   }

   private String mapClassName(String name) {
      WasmGCVirtualTableBuilder.Table table = this.tableMap.get(name);
      return table == null ? name : table.resolve().cls.getName();
   }

   private void moveClassesToMergedTables() {
      for (WasmGCVirtualTableBuilder.Table table : this.tables) {
         if (table.parent != null) {
            table.parent = table.parent.resolve();
         }

         WasmGCVirtualTableBuilder.Table resolvedTable = table.resolve();
         if (resolvedTable != table) {
            if (resolvedTable.mergedClasses == null) {
               resolvedTable.mergedClasses = new ArrayList<>();
            }

            resolvedTable.mergedClasses.add(table.cls);
         }
      }
   }

   private void fillTables() {
      for (String className : this.classes.getClassNames()) {
         WasmGCVirtualTableBuilder.Table table = this.tableMap.get(className);
         if (table != null) {
            this.fillTable(table);
         }
      }
   }

   private void fillTable(WasmGCVirtualTableBuilder.Table table) {
      if (!table.filled) {
         table.filled = true;
         WasmGCVirtualTableBuilder.Table parent = table.parent;
         ObjectIntHashMap<MethodDescriptor> indexes = new ObjectIntHashMap();
         if (parent != null) {
            this.fillTable(parent);
            table.entries.addAll(parent.entries);
            table.implementors.addAll(parent.implementors);

            for (WasmGCVirtualTableBuilder.Entry entry : table.entries) {
               indexes.put(entry.method, entry.index);
            }

            table.currentImplementors.putAll(parent.currentImplementors);
            table.implementorsFromInterfaces.addAll(parent.implementorsFromInterfaces);
            table.interfaces.addAll(parent.interfaces);
         } else {
            table.used = true;
         }

         ArrayList<ClassReader> classes = new ArrayList<>();
         classes.add(table.cls);
         if (table.mergedClasses != null) {
            classes.addAll(table.mergedClasses);
         }

         if (!table.cls.hasModifier(ElementModifier.INTERFACE)) {
            for (String itfName : table.cls.getInterfaces()) {
               this.fillFromInterfaces(itfName, table);
            }

            for (ClassReader cls : classes) {
               for (MethodReader method : cls.getMethods()) {
                  if (!method.hasModifier(ElementModifier.STATIC) && method.getLevel() != AccessLevel.PRIVATE) {
                     if (!method.hasModifier(ElementModifier.ABSTRACT)) {
                        if ((method.getProgram() != null || method.hasModifier(ElementModifier.NATIVE))
                           && (this.isVirtual.test(method.getReference()) || method.getReference().equals(CLONE_METHOD))) {
                           table.currentImplementors.put(method.getDescriptor(), method.getReference());
                           table.implementorsFromInterfaces.remove(method.getDescriptor());
                        }
                     } else {
                        table.currentImplementors.remove(method.getDescriptor());
                     }
                  }
               }
            }
         }

         Set<MethodDescriptor> group = this.groupedMethodsAtCallSites.get(table.cls.getName());
         if (group != null) {
            table.used = true;

            for (MethodDescriptor method : group) {
               if (indexes.getOrDefault(method, -1) < 0) {
                  WasmGCVirtualTableBuilder.Entry entry = new WasmGCVirtualTableBuilder.Entry(method, table, table.entries.size());
                  table.entries.add(entry);
                  indexes.put(method, entry.index);
                  table.implementors.add(null);
               }
            }
         }

         for (ObjectIntCursor<MethodDescriptor> entry : indexes) {
            MethodReference implementor = table.currentImplementors.get(entry.key);
            table.implementors.set(entry.value, implementor);
         }
      }
   }

   private void fillFromInterfaces(String itfName, WasmGCVirtualTableBuilder.Table table) {
      if (table.interfaces.add(itfName)) {
         ClassReader cls = this.classes.get(itfName);
         if (cls != null) {
            for (String superItf : cls.getInterfaces()) {
               this.fillFromInterfaces(superItf, table);
            }

            for (MethodReader method : cls.getMethods()) {
               if (!method.hasModifier(ElementModifier.STATIC)
                  && !method.hasModifier(ElementModifier.ABSTRACT)
                  && method.getLevel() != AccessLevel.PRIVATE
                  && (table.currentImplementors.get(method.getDescriptor()) == null || table.implementorsFromInterfaces.contains(method.getDescriptor()))
                  && (method.getProgram() != null || method.hasModifier(ElementModifier.NATIVE))
                  && this.isVirtual.test(method.getReference())) {
                  table.currentImplementors.put(method.getDescriptor(), method.getReference());
                  table.implementorsFromInterfaces.add(method.getDescriptor());
               }
            }
         }
      }
   }

   private void buildResult() {
      for (WasmGCVirtualTableBuilder.Table table : this.tables) {
         this.result.put(table.cls.getName(), table.getBuildResult());
      }
   }

   private static class Entry {
      MethodDescriptor method;
      WasmGCVirtualTableBuilder.Table origin;
      int index;
      private WasmGCVirtualTableEntry buildResult;

      Entry(MethodDescriptor method, WasmGCVirtualTableBuilder.Table origin, int index) {
         this.method = method;
         this.origin = origin;
         this.index = index;
      }

      WasmGCVirtualTableEntry getBuildResult() {
         if (this.buildResult == null) {
            this.buildResult = new WasmGCVirtualTableEntry(this.origin.getBuildResult(), this.method, this.index);
         }

         return this.buildResult;
      }
   }

   private static class Table {
      boolean visited;
      int depth = -1;
      Set<WasmGCVirtualTableBuilder.Table> liftedInterfaces;
      WasmGCVirtualTableBuilder.Table reference;
      WasmGCVirtualTableBuilder.Table commonImplementor;
      boolean commonImplementorFilled;
      boolean interfaceMergedIntoClass;
      final ClassReader cls;
      List<ClassReader> mergedClasses;
      int index;
      boolean filled;
      boolean used;
      WasmGCVirtualTableBuilder.Table parent;
      List<WasmGCVirtualTableBuilder.Entry> entries = new ArrayList<>();
      List<MethodReference> implementors = new ArrayList<>();
      Map<MethodDescriptor, MethodReference> currentImplementors = new HashMap<>();
      Set<MethodDescriptor> implementorsFromInterfaces = new HashSet<>();
      Set<String> interfaces = new HashSet<>();
      private WasmGCVirtualTable buildResult;
      private boolean building;
      private boolean resolving;

      Table(ClassReader cls, int index) {
         this.cls = cls;
         this.index = index;
      }

      void merge(WasmGCVirtualTableBuilder.Table other) {
         other.reference = this;
      }

      WasmGCVirtualTableBuilder.Table atDepth(int depth) {
         if (depth > this.depth) {
            return null;
         }

         WasmGCVirtualTableBuilder.Table result = this;

         while (depth < result.depth) {
            result = result.parent;
         }

         return result;
      }

      WasmGCVirtualTable getBuildResult() {
         if (this.reference != null) {
            return this.resolve().getBuildResult();
         }

         if (this.buildResult == null) {
            if (this.building) {
               throw new IllegalStateException();
            }

            this.building = true;
            this.buildResult = new WasmGCVirtualTable(
               this.parent != null ? this.parent.getBuildResult() : null, this.cls.getName(), this.used, !this.cls.hasModifier(ElementModifier.ABSTRACT)
            );
            this.buildResult.entries = this.entries.stream().map(WasmGCVirtualTableBuilder.Entry::getBuildResult).collect(Collectors.toList());
            this.buildResult.implementors = this.implementors.toArray(new MethodReference[0]);
            this.buildResult.fakeInterfaceRepresentative = this.cls.hasModifier(ElementModifier.INTERFACE);
         }

         return this.buildResult;
      }

      WasmGCVirtualTableBuilder.Table resolve() {
         if (this.reference != null) {
            if (this.resolving) {
               throw new IllegalStateException();
            }

            this.resolving = true;
            this.reference = this.reference.resolve();
            this.resolving = false;
            return this.reference;
         } else {
            return this;
         }
      }
   }
}
