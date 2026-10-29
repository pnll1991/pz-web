package org.teavm.backend.wasm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.backend.wasm.model.instruction.WasmDefaultInstructionVisitor;
import org.teavm.backend.wasm.model.instruction.WasmGetGlobal;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;

public class WasmModule {
   private int minMemorySize;
   private int maxMemorySize;
   private List<WasmMemorySegment> segments = new ArrayList<>();
   private List<WasmFunction> functionTable = new ArrayList<>();
   private WasmFunction startFunction;
   private Map<String, WasmCustomSection> customSections = new LinkedHashMap<>();
   private Map<String, WasmCustomSection> readonlyCustomSections = Collections.unmodifiableMap(this.customSections);
   public final WasmCollection<WasmFunction> functions = new WasmCollection<>();
   public final WasmCollection<WasmGlobal> globals = new WasmCollection<>();
   public final WasmCollection<WasmCompositeType> types = new WasmCollection<>();
   public final WasmCollection<WasmTag> tags = new WasmCollection<>();
   public String memoryExportName;
   public String memoryImportName;
   public String memoryImportModule;
   public boolean sharedMemory;

   public void add(WasmCustomSection customSection) {
      if (this.customSections.containsKey(customSection.getName())) {
         throw new IllegalArgumentException("Custom section " + customSection.getName() + " already defined in this module");
      }

      if (customSection.module != null) {
         throw new IllegalArgumentException("Given custom section is already registered in another module");
      }

      this.customSections.put(customSection.getName(), customSection);
      customSection.module = this;
   }

   public void remove(WasmCustomSection customSection) {
      if (customSection.module == this) {
         customSection.module = null;
         this.customSections.remove(customSection.getName());
      }
   }

   public Map<? extends String, ? extends WasmCustomSection> getCustomSections() {
      return this.readonlyCustomSections;
   }

   public List<WasmFunction> getFunctionTable() {
      return this.functionTable;
   }

   public List<WasmMemorySegment> getSegments() {
      return this.segments;
   }

   public int getMinMemorySize() {
      return this.minMemorySize;
   }

   public void setMinMemorySize(int minMemorySize) {
      this.minMemorySize = minMemorySize;
   }

   public int getMaxMemorySize() {
      return this.maxMemorySize;
   }

   public void setMaxMemorySize(int maxMemorySize) {
      this.maxMemorySize = maxMemorySize;
   }

   public WasmFunction getStartFunction() {
      return this.startFunction;
   }

   public void setStartFunction(WasmFunction startFunction) {
      this.startFunction = startFunction;
   }

   public void prepareForRendering() {
      this.prepareGlobals();
      this.prepareTypes();
   }

   private void prepareGlobals() {
      WasmModule.GlobalSorting sorting = new WasmModule.GlobalSorting();
      sorting.sort(this.globals);
      this.globals.clear();

      for (WasmGlobal global : sorting.sorted) {
         this.globals.add(global);
      }
   }

   private void prepareTypes() {
      Graph typeGraph = WasmTypeGraphBuilder.buildTypeGraph(this, this.types, this.types.size());
      int[][] sccs = GraphUtils.findStronglyConnectedComponents(typeGraph);
      int[] sccStartNode = new int[this.types.size()];
      int i = 0;

      while (i < sccStartNode.length) {
         sccStartNode[i] = i++;
      }

      int[][] sccsByIndex = new int[this.types.size()][];

      for (int[] scc : sccs) {
         sccsByIndex[scc[0]] = scc;

         for (int ix = 0; ix < scc.length; ix++) {
            sccStartNode[scc[ix]] = sccStartNode[scc[0]];
         }
      }

      WasmModule.TypeSorting sorting = new WasmModule.TypeSorting();
      sorting.original = this.types;
      sorting.graph = typeGraph;
      sorting.visited = new boolean[this.types.size()];
      sorting.sccVisited = new boolean[this.types.size()];
      sorting.sccMap = sccStartNode;
      sorting.sccsByIndex = sccsByIndex;

      for (int ix = 0; ix < this.types.size(); ix++) {
         sorting.visit(ix);
      }

      this.types.clear();

      for (WasmCompositeType type : sorting.sorted) {
         this.types.add(type);
      }
   }

   private static class GlobalSorting extends WasmDefaultInstructionVisitor {
      List<WasmGlobal> sorted = new ArrayList<>();
      private Set<WasmGlobal> visited = new HashSet<>();

      void sort(Iterable<WasmGlobal> globals) {
         for (WasmGlobal global : globals) {
            this.add(global);
         }
      }

      private void add(WasmGlobal global) {
         if (this.visited.add(global)) {
            this.visitMany(global.getInitialValue());
            this.sorted.add(global);
         }
      }

      @Override
      public void visit(WasmGetGlobal instruction) {
         this.add(instruction.getGlobal());
      }
   }

   private static class TypeSorting {
      WasmCollection<WasmCompositeType> original;
      Graph graph;
      boolean[] visited;
      boolean[] sccVisited;
      int[] sccMap;
      int[][] sccsByIndex;
      List<WasmCompositeType> sorted = new ArrayList<>();

      void visit(int typeIndex) {
         typeIndex = this.sccMap[typeIndex];
         if (!this.visited[typeIndex]) {
            int[] scc = this.sccsByIndex[typeIndex];
            if (scc == null) {
               this.visited[typeIndex] = true;

               for (int outgoing : this.graph.outgoingEdges(typeIndex)) {
                  this.visit(outgoing);
               }

               this.sorted.add(this.original.get(typeIndex));
            } else {
               this.visited[typeIndex] = true;

               for (int index : scc) {
                  for (int outgoing : this.graph.outgoingEdges(index)) {
                     this.visit(outgoing);
                  }
               }

               int indexOfFirst = this.sorted.size();

               for (int index : scc) {
                  this.visitScc(index, typeIndex);
               }

               this.sorted.get(indexOfFirst).recursiveTypeCount = scc.length;
            }
         }
      }

      void visitScc(int index, int sccBase) {
         if (!this.sccVisited[index]) {
            this.sccVisited[index] = true;
            WasmCompositeType type = this.original.get(index);
            if (type instanceof WasmStructure) {
               WasmStructure supertype = ((WasmStructure)type).getSupertype();
               if (supertype != null && this.sccMap[supertype.index] == sccBase) {
                  this.visitScc(supertype.index, sccBase);
               }
            } else if (type instanceof WasmFunctionType) {
               for (WasmFunctionType supertype : ((WasmFunctionType)type).getSupertypes()) {
                  if (this.sccMap[supertype.index] == sccBase) {
                     this.visitScc(supertype.index, sccBase);
                  }
               }
            }

            this.sorted.add(type);
         }
      }
   }
}
