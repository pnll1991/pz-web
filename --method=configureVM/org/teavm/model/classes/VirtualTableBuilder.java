package org.teavm.model.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.common.LCATree;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.hppc.cursors.IntCursor;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.util.GraphColorer;

public class VirtualTableBuilder {
   private ListableClassReaderSource classes;
   private Map<String, List<MethodDescriptor>> methodsUsedAtCallSites = new HashMap<>();
   private Predicate<MethodReference> methodCalledVirtually = m -> true;
   private Map<String, VirtualTableBuilder.TableBuilder> tables;
   private Map<String, List<String>> classChildren;
   private LCATree classTree;
   private ObjectIntMap<String> classTreeIndexes;
   private List<String> classList;
   private VirtualTableProvider result;
   private List<MethodDescriptor> methodDescriptors;
   private ObjectIntMap<MethodDescriptor> methodDescriptorIndexes;
   private int[] methodColors;
   private int methodColorCount;

   public VirtualTableBuilder(ListableClassReaderSource classes) {
      this.classes = classes;
   }

   public void setMethodsUsedAtCallSites(Collection<? extends MethodReference> methodsUsedAtCallSites) {
      for (MethodReference method : methodsUsedAtCallSites) {
         this.methodsUsedAtCallSites.computeIfAbsent(method.getClassName(), k -> new ArrayList<>()).add(method.getDescriptor());
      }
   }

   public void setMethodCalledVirtually(Predicate<MethodReference> methodCalledVirtually) {
      this.methodCalledVirtually = methodCalledVirtually;
   }

   public VirtualTableProvider build() {
      this.tables = new HashMap<>();
      this.buildVirtualTables();
      this.cleanupVirtualTables();
      this.classChildren = new HashMap<>();
      this.buildClassChildren();
      this.pack();
      this.liftEntries();
      this.buildResult();
      this.tables = null;
      return this.result;
   }

   private void buildVirtualTables() {
      for (String className : this.classes.getClassNames()) {
         this.fillClass(className);
      }
   }

   private void fillClass(String className) {
      ClassReader cls = this.classes.get(className);
      if (cls != null) {
         if (!this.tables.containsKey(className)) {
            VirtualTableBuilder.TableBuilder table = new VirtualTableBuilder.TableBuilder();
            this.tables.put(className, table);
            String parent = cls.getParent();
            if (parent != null) {
               this.fillClass(parent);
               VirtualTableBuilder.TableBuilder parentTable = this.tables.get(parent);
               if (parentTable != null) {
                  this.copyEntries(parentTable, table);
                  table.entriesFromInterfaces.addAll(parentTable.entriesFromInterfaces);
                  table.processedInterfaces.addAll(parentTable.processedInterfaces);
               }
            }

            if (cls.hasModifier(ElementModifier.INTERFACE)) {
               for (String itf : cls.getInterfaces()) {
                  this.fillClass(itf);
                  VirtualTableBuilder.TableBuilder itfTable = this.tables.get(itf);
                  if (itfTable != null) {
                     this.copyEntries(itfTable, table);
                  }
               }
            } else {
               for (String itf : cls.getInterfaces()) {
                  this.fillFromInterfaces(itf, table);
               }
            }

            List<MethodDescriptor> methodsAtCallSites = this.methodsUsedAtCallSites.get(className);
            if (methodsAtCallSites != null) {
               for (MethodDescriptor methodDesc : methodsAtCallSites) {
                  MethodReader method = cls.getMethod(methodDesc);
                  if (method == null || method.getLevel() != AccessLevel.PRIVATE) {
                     table.entries.computeIfAbsent(methodDesc, k -> new VirtualTableBuilder.EntryBuilder());
                  }
               }
            }

            for (MethodReader method : cls.getMethods()) {
               if (!method.hasModifier(ElementModifier.STATIC) && !method.getName().equals("<init>") && method.getLevel() != AccessLevel.PRIVATE) {
                  if (method.hasModifier(ElementModifier.ABSTRACT)) {
                     if (!cls.hasModifier(ElementModifier.INTERFACE)) {
                        VirtualTableBuilder.EntryBuilder entry = table.entries.get(method.getDescriptor());
                        if (entry != null) {
                           entry.implementor = null;
                        }

                        table.entriesFromInterfaces.remove(method.getDescriptor());
                     }
                  } else {
                     VirtualTableBuilder.EntryBuilder entry = table.entries.get(method.getDescriptor());
                     if (entry == null) {
                        if (cls.hasModifier(ElementModifier.FINAL)) {
                           continue;
                        }

                        entry = new VirtualTableBuilder.EntryBuilder();
                        table.entries.put(method.getDescriptor(), entry);
                     }

                     entry.implementor = method.getReference();
                     table.entriesFromInterfaces.remove(method.getDescriptor());
                  }
               }
            }
         }
      }
   }

   private void fillFromInterfaces(String itfName, VirtualTableBuilder.TableBuilder table) {
      if (table.processedInterfaces.add(itfName)) {
         this.fillClass(itfName);
         ClassReader itf = this.classes.get(itfName);
         if (itf != null) {
            VirtualTableBuilder.TableBuilder itfTable = this.tables.get(itfName);

            for (String superItf : itf.getInterfaces()) {
               this.fillFromInterfaces(superItf, table);
            }

            if (itfTable != null) {
               for (MethodDescriptor descriptor : itfTable.entries.keySet()) {
                  table.entries.computeIfAbsent(descriptor, k -> new VirtualTableBuilder.EntryBuilder());
               }
            }

            for (MethodReader method : itf.getMethods()) {
               if (!method.hasModifier(ElementModifier.STATIC) && !method.hasModifier(ElementModifier.ABSTRACT) && method.getLevel() != AccessLevel.PRIVATE) {
                  MethodDescriptor descriptor = method.getDescriptor();
                  VirtualTableBuilder.EntryBuilder existing = table.entries.get(descriptor);
                  if (existing == null || existing.implementor == null || table.entriesFromInterfaces.contains(descriptor)) {
                     VirtualTableBuilder.EntryBuilder entry = table.entries.computeIfAbsent(descriptor, k -> new VirtualTableBuilder.EntryBuilder());
                     entry.implementor = method.getReference();
                     table.entriesFromInterfaces.add(descriptor);
                  }
               }
            }
         }
      }
   }

   private void copyEntries(VirtualTableBuilder.TableBuilder source, VirtualTableBuilder.TableBuilder target) {
      for (Entry<MethodDescriptor, VirtualTableBuilder.EntryBuilder> entry : source.entries.entrySet()) {
         VirtualTableBuilder.EntryBuilder targetEntry = target.entries.computeIfAbsent(entry.getKey(), k -> new VirtualTableBuilder.EntryBuilder());
         targetEntry.addParent(entry.getValue());
         if (entry.getValue().implementor != null && targetEntry.implementor == null) {
            targetEntry.implementor = entry.getValue().implementor;
         }
      }
   }

   private void cleanupVirtualTables() {
      for (String className : this.classes.getClassNames()) {
         VirtualTableBuilder.TableBuilder table = this.tables.get(className);

         for (MethodDescriptor method : table.entries.keySet().toArray(new MethodDescriptor[0])) {
            VirtualTableBuilder.EntryBuilder entry = table.entries.get(method);
            if (entry.implementor != null && !this.methodCalledVirtually.test(entry.implementor)) {
               entry.implementor = null;
            }
         }
      }
   }

   private void buildClassChildren() {
      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (!cls.hasModifier(ElementModifier.INTERFACE) && cls.getParent() != null) {
            this.classChildren.computeIfAbsent(cls.getParent(), c -> new ArrayList<>()).add(className);
         }
      }
   }

   private void liftEntries() {
      this.buildClassTree();

      for (Entry<MethodDescriptor, List<String>> group : this.groupMethods().entrySet()) {
         String commonSuperclass = this.commonSuperclass(group.getValue());
         Set<String> visited = new HashSet<>();

         for (String cls : group.getValue()) {
            this.liftEntriesAtTable(cls, commonSuperclass, group.getKey(), visited);
         }
      }

      this.classTree = null;
      this.classTreeIndexes = null;
      this.classList = null;
   }

   private void buildClassTree() {
      this.classTree = new LCATree(this.classes.getClassNames().size());
      this.classTreeIndexes = new ObjectIntHashMap();
      this.classList = new ArrayList<>();
      this.classList.add(null);

      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (!cls.hasModifier(ElementModifier.INTERFACE)) {
            this.insertClassToTree(className);
         }
      }
   }

   private int insertClassToTree(String className) {
      int index = this.classTreeIndexes.getOrDefault(className, 0);
      if (index == 0) {
         ClassReader cls = this.classes.get(className);
         int parent = cls != null && cls.getParent() != null ? this.insertClassToTree(cls.getParent()) : 0;
         index = this.classTree.addNode(parent);
         this.classList.add(className);
         this.classTreeIndexes.put(className, index);
      }

      return index;
   }

   private String commonSuperclass(List<String> classNames) {
      int result = this.classTreeIndexes.get(classNames.get(0));

      for (int i = 1; i < classNames.size(); i++) {
         int next = this.classTreeIndexes.get(classNames.get(i));
         result = this.classTree.lcaOf(result, next);
      }

      return this.classList.get(result);
   }

   private Map<MethodDescriptor, List<String>> groupMethods() {
      Map<MethodDescriptor, List<String>> groups = new LinkedHashMap<>();

      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (!cls.hasModifier(ElementModifier.INTERFACE)) {
            VirtualTableBuilder.TableBuilder table = this.tables.get(className);
            VirtualTableBuilder.TableBuilder parentTable = cls.getParent() != null ? this.tables.get(cls.getParent()) : null;

            for (MethodDescriptor method : table.entries.keySet()) {
               VirtualTableBuilder.EntryBuilder entry = table.entries.get(method);
               if (entry.implementor != null) {
                  if (parentTable != null) {
                     VirtualTableBuilder.EntryBuilder parentEntry = parentTable.entries.get(method);
                     if (parentEntry != null && entry.implementor.equals(parentEntry.implementor)) {
                        continue;
                     }
                  }

                  groups.computeIfAbsent(method, k -> new ArrayList<>()).add(className);
               }
            }
         }
      }

      groups.entrySet().removeIf(entryx -> ((List)entryx.getValue()).size() == 1);
      return groups;
   }

   private void liftEntriesAtTable(String className, String toClass, MethodDescriptor method, Set<String> visited) {
      while (visited.add(className)) {
         VirtualTableBuilder.TableBuilder table = this.tables.get(className);
         if (table != null) {
            VirtualTableBuilder.EntryBuilder entry = table.entries.get(method);
            if (entry == null) {
               entry = new VirtualTableBuilder.EntryBuilder();
               entry.fake = true;
               table.entries.put(method, entry);
            }

            if (!className.equals(toClass)) {
               ClassReader cls = this.classes.get(className);
               if (cls != null) {
                  className = cls.getParent();
                  continue;
               }
            }
         }
         break;
      }
   }

   private void pack() {
      this.methodDescriptorIndexes = new ObjectIntHashMap();
      this.methodDescriptors = new ArrayList<>();
      GraphBuilder graphBuilder = new GraphBuilder();

      for (String className : this.classes.getClassNames()) {
         VirtualTableBuilder.TableBuilder table = this.tables.get(className);
         MethodDescriptor[] methods = table.entries.keySet().toArray(new MethodDescriptor[0]);

         for (int i = 0; i < methods.length; i++) {
            for (int j = i + 1; j < methods.length; j++) {
               int a = this.getMethodIndex(methods[i]);
               int b = this.getMethodIndex(methods[j]);
               graphBuilder.addEdge(a, b);
               graphBuilder.addEdge(b, a);
            }
         }
      }

      Graph interferenceGraph = graphBuilder.build();
      this.methodColors = new int[this.methodDescriptors.size()];
      Arrays.fill(this.methodColors, -1);
      new GraphColorer().colorize(interferenceGraph, this.methodColors);
      int colorCount = 0;

      for (int i = 0; i < this.methodColors.length; i++) {
         colorCount = Math.max(colorCount, this.methodColors[i]--);
      }

      this.methodColorCount = colorCount;
   }

   private int getMethodIndex(MethodDescriptor method) {
      int index = this.methodDescriptorIndexes.getOrDefault(method, -1);
      if (index < 0) {
         index = this.methodDescriptors.size();
         this.methodDescriptors.add(method);
         this.methodDescriptorIndexes.put(method, index);
      }

      return index;
   }

   private void buildResult() {
      this.result = new VirtualTableProvider();
      this.buildResultForClasses();
      this.buildResultForInterfaces();
   }

   private void buildResultForClasses() {
      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (!cls.hasModifier(ElementModifier.INTERFACE) && cls.getParent() == null) {
            VirtualTableBuilder.Context context = new VirtualTableBuilder.Context();
            context.indexes = new int[this.methodColorCount];
            Arrays.fill(context.indexes, -1);
            this.buildResultForClass(className, context, null);
         }
      }
   }

   private void buildResultForClass(String className, VirtualTableBuilder.Context context, VirtualTable parent) {
      VirtualTableBuilder.TableBuilder table = this.tables.get(className);
      ClassReader cls = this.classes.get(className);
      int colorsStart = context.colors.size();
      int initialMethodsStart = parent != null ? parent.size() : 0;
      int methodsStart = initialMethodsStart;
      IntArrayList resolvedIndexes = new IntArrayList();
      Map<MethodDescriptor, VirtualTableEntry> resultEntries = new HashMap<>();

      for (MethodDescriptor method : table.entries.keySet()) {
         int color = this.methodColors[this.methodDescriptorIndexes.get(method)];
         VirtualTableBuilder.EntryBuilder entry = table.entries.get(method);
         int index = context.indexes[color];
         if (index < 0) {
            index = context.colors.size();
            context.indexes[color] = index;
            context.colors.add(color);
            context.methods.add(null);
         }

         if (entry.implementor != null) {
            VirtualTableEntry resultEntry = new VirtualTableEntry(method, entry.implementor, index);
            resultEntries.put(method, resultEntry);
            this.propagateInterfaceIndexes(cls, method, index);
         }

         if (context.methods.get(index) == null && !entry.fake) {
            context.methods.set(index, method);
            resolvedIndexes.add(index);

            while (index < methodsStart) {
               methodsStart -= parent.getMethods().size();
               parent = parent.getParent();
               if (parent == null) {
                  break;
               }
            }
         }
      }

      List<MethodDescriptor> newMethods = context.methods.subList(methodsStart, context.methods.size());
      Set<MethodDescriptor> methodSet = new HashSet<>();

      for (MethodDescriptor method : newMethods) {
         if (method != null) {
            methodSet.add(method);
         }
      }

      List<MethodDescriptor> readonlyNewMethods = Collections.unmodifiableList(Arrays.asList(newMethods.toArray(new MethodDescriptor[0])));
      VirtualTable resultTable = new VirtualTable(className, parent, readonlyNewMethods, methodSet, resultEntries);
      this.result.virtualTables.put(className, resultTable);
      List<String> children = this.classChildren.get(className);
      if (children != null) {
         for (String child : children) {
            this.buildResultForClass(child, context, resultTable);
         }
      }

      for (int i = colorsStart; i < context.colors.size(); i++) {
         context.indexes[context.colors.get(i)] = -1;
      }

      context.colors.removeRange(colorsStart, context.colors.size());

      for (IntCursor resolvedIndex : resolvedIndexes) {
         context.methods.set(resolvedIndex.value, null);
      }

      context.methods.subList(initialMethodsStart, context.methods.size()).clear();
   }

   private void propagateInterfaceIndexes(ClassReader cls, MethodDescriptor method, int index) {
      while (true) {
         for (String itf : cls.getInterfaces()) {
            VirtualTableBuilder.TableBuilder itfTable = this.tables.get(itf);
            if (itfTable != null) {
               VirtualTableBuilder.EntryBuilder itfEntry = itfTable.entries.get(method);
               if (itfEntry != null) {
                  this.propagateInterfaceIndex(itfEntry, index);
               }
            }
         }

         if (cls.getParent() != null) {
            cls = this.classes.get(cls.getParent());
            if (cls != null) {
               VirtualTableBuilder.TableBuilder table = this.tables.get(cls.getName());
               VirtualTableBuilder.EntryBuilder entry = table.entries.get(method);
               if (entry != null && entry.implementor == null) {
                  continue;
               }
            }
         }

         return;
      }
   }

   private void propagateInterfaceIndex(VirtualTableBuilder.EntryBuilder entry, int index) {
      if (entry.index < 0) {
         entry.index = index;
         if (entry.parents != null) {
            for (VirtualTableBuilder.EntryBuilder parent : entry.parents) {
               this.propagateInterfaceIndex(parent, index);
            }
         }
      }
   }

   private void buildResultForInterfaces() {
      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (cls.hasModifier(ElementModifier.INTERFACE)) {
            List<MethodDescriptor> methods = new ArrayList<>();
            Set<MethodDescriptor> methodSet = new HashSet<>();
            VirtualTableBuilder.TableBuilder table = this.tables.get(className);

            for (MethodDescriptor method : table.entries.keySet()) {
               VirtualTableBuilder.EntryBuilder entry = table.entries.get(method);
               if (entry.index >= 0) {
                  if (entry.index >= methods.size()) {
                     methods.addAll(Collections.nCopies(entry.index - methods.size() + 1, null));
                  }

                  methods.set(entry.index, method);
                  methodSet.add(method);
               }
            }

            List<MethodDescriptor> readonlyMethods = Collections.unmodifiableList(Arrays.asList(methods.toArray(new MethodDescriptor[0])));
            VirtualTable resultTable = new VirtualTable(className, null, readonlyMethods, methodSet, Map.of());
            this.result.virtualTables.put(className, resultTable);
         }
      }
   }

   public static Set<MethodReference> getMethodsUsedOnCallSites(ListableClassHolderSource classes, boolean withCloneArray) {
      HashSet<MethodReference> virtualMethods = new HashSet<>();

      for (String className : classes.getClassNames()) {
         ClassHolder cls = classes.get(className);

         for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program != null) {
               for (int i = 0; i < program.basicBlockCount(); i++) {
                  for (Instruction insn : program.basicBlockAt(i)) {
                     if (insn instanceof InvokeInstruction invoke) {
                        if (invoke.getType() == InvocationType.VIRTUAL) {
                           virtualMethods.add(invoke.getMethod());
                        }
                     } else if (insn instanceof CloneArrayInstruction && withCloneArray) {
                        virtualMethods.add(new MethodReference(Object.class, "clone", Object.class));
                     }
                  }
               }
            }
         }
      }

      return virtualMethods;
   }

   static class Context {
      int[] indexes;
      IntArrayList colors = new IntArrayList();
      List<MethodDescriptor> methods = new ArrayList<>();
   }

   static class EntryBuilder {
      MethodReference implementor;
      VirtualTableBuilder.EntryBuilder[] parents;
      int index = -1;
      boolean fake;

      void addParent(VirtualTableBuilder.EntryBuilder parent) {
         if (this.parents == null) {
            this.parents = new VirtualTableBuilder.EntryBuilder[]{parent};
         } else {
            this.parents = Arrays.copyOf(this.parents, this.parents.length + 1);
            this.parents[this.parents.length - 1] = parent;
         }
      }
   }

   static class TableBuilder {
      Map<MethodDescriptor, VirtualTableBuilder.EntryBuilder> entries = new LinkedHashMap<>();
      Set<MethodDescriptor> entriesFromInterfaces = new HashSet<>();
      Set<String> processedInterfaces = new HashSet<>();
   }
}
