package org.teavm.backend.wasm.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;

final class WasmTypeGraphBuilder {
   private WasmTypeGraphBuilder() {
   }

   static Graph buildTypeGraph(WasmModule module, Iterable<WasmCompositeType> types, int size) {
      GraphBuilder graphBuilder = new GraphBuilder(size);
      WasmTypeGraphBuilder.GraphBuilderVisitor visitor = new WasmTypeGraphBuilder.GraphBuilderVisitor(module, graphBuilder);

      for (WasmCompositeType type : types) {
         visitor.currentIndex = module.types.indexInCollection(type);
         type.acceptVisitor(visitor);
      }

      addNominalStructures(module, types, graphBuilder);
      return graphBuilder.build();
   }

   private static void addNominalStructures(WasmModule module, Iterable<WasmCompositeType> types, GraphBuilder graphBuilder) {
      HashMap<WasmStructure, List<WasmStructure>> subStructures = new HashMap<>();
      ArrayList<WasmStructure> topLevelStructures = new ArrayList<>();

      for (WasmCompositeType type : types) {
         if (type instanceof WasmStructure structure && structure.isNominal()) {
            if (structure.getSupertype() != null) {
               subStructures.computeIfAbsent(structure.getSupertype(), k -> new ArrayList<>()).add(structure);
            } else {
               topLevelStructures.add(structure);
            }
         }
      }

      mergeNominalStructures(module, topLevelStructures, 0, graphBuilder);

      for (Entry<WasmStructure, List<WasmStructure>> entry : subStructures.entrySet()) {
         mergeNominalStructures(module, entry.getValue(), entry.getKey().getFields().size(), graphBuilder);
      }
   }

   private static void mergeNominalStructures(WasmModule module, List<WasmStructure> structures, int parentFieldCount, GraphBuilder graphBuilder) {
      for (int i = 0; i < structures.size(); i++) {
         for (int j = i + 1; j < structures.size(); j++) {
            WasmStructure a = structures.get(i);
            WasmStructure b = structures.get(j);
            if (areSameStructures(parentFieldCount, a, b)) {
               int p = module.types.indexInCollection(a);
               int q = module.types.indexInCollection(b);
               graphBuilder.addEdge(p, q);
               graphBuilder.addEdge(q, p);
               break;
            }
         }
      }
   }

   private static boolean areSameStructures(int start, WasmStructure a, WasmStructure b) {
      if (a.getFields().size() != b.getFields().size()) {
         return false;
      }

      for (int i = start; i < a.getFields().size(); i++) {
         if (a.getFields().get(i).getType() != b.getFields().get(i).getType()) {
            return false;
         }
      }

      return true;
   }

   private static class GraphBuilderVisitor implements WasmCompositeTypeVisitor {
      final WasmModule module;
      final GraphBuilder graphBuilder;
      int currentIndex;

      GraphBuilderVisitor(WasmModule module, GraphBuilder graphBuilder) {
         this.module = module;
         this.graphBuilder = graphBuilder;
      }

      @Override
      public void visit(WasmStructure type) {
         if (type.getSupertype() != null) {
            this.addEdge(type.getSupertype().getReference());
         }

         for (WasmField field : type.getFields()) {
            this.addEdge(field.getUnpackedType());
         }
      }

      @Override
      public void visit(WasmArray type) {
         this.addEdge(type.getElementType().asUnpackedType());
      }

      @Override
      public void visit(WasmFunctionType type) {
         for (WasmFunctionType supertype : type.getSupertypes()) {
            this.addEdge(supertype.getReference());
         }

         for (WasmType parameter : type.getParameterTypes()) {
            this.addEdge(parameter);
         }

         for (WasmType ret : type.getReturnTypes()) {
            this.addEdge(ret);
         }
      }

      private void addEdge(WasmType type) {
         if (type instanceof WasmType.CompositeReference) {
            WasmCompositeType composite = ((WasmType.CompositeReference)type).composite;
            this.graphBuilder.addEdge(this.currentIndex, this.module.types.indexInCollection(composite));
         }
      }
   }
}
