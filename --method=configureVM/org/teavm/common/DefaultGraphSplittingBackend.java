package org.teavm.common;

import org.teavm.hppc.IntIntHashMap;

public class DefaultGraphSplittingBackend implements GraphSplittingBackend {
   private MutableDirectedGraph graph;
   private int index;
   private IntegerArray prototypeNodes;
   private IntegerArray copyIndexes;
   private int[] copyCount;

   public DefaultGraphSplittingBackend(Graph graph) {
      this.graph = new MutableDirectedGraph(graph);
      this.prototypeNodes = new IntegerArray(graph.size());
      this.copyIndexes = new IntegerArray(graph.size());
      this.copyCount = new int[graph.size()];
      this.index = graph.size();

      for (int i = 0; i < graph.size(); i++) {
         this.prototypeNodes.add(i);
         this.copyIndexes.add(0);
      }
   }

   public Graph getGraph() {
      return this.graph.copyToImmutable();
   }

   public int prototype(int index) {
      return this.prototypeNodes.get(index);
   }

   @Override
   public int[] split(int[] remaining, int[] toCopy) {
      int[] copies = new int[toCopy.length];
      IntIntHashMap map = new IntIntHashMap();

      for (int i = 0; i < toCopy.length; i++) {
         copies[i] = this.index++;
         map.put(toCopy[i], copies[i]);
         int proto = this.prototypeNodes.get(toCopy[i]);
         this.prototypeNodes.add(proto);
         this.copyIndexes.add(++this.copyCount[proto]);
      }

      for (int i = 0; i < remaining.length; i++) {
         int node = remaining[i];

         for (int succ : this.graph.outgoingEdges(node)) {
            int succCopy = map.getOrDefault(succ, -1);
            if (succCopy >= 0) {
               this.graph.deleteEdge(node, succ);
               this.graph.addEdge(node, succCopy);
            }
         }
      }

      for (int i = 0; i < toCopy.length; i++) {
         int node = toCopy[i];
         int nodeCopy = copies[i];

         for (int succ : this.graph.outgoingEdges(node)) {
            int succCopy = map.getOrDefault(succ, -1);
            if (succCopy >= 0) {
               this.graph.addEdge(nodeCopy, succCopy);
            } else {
               this.graph.addEdge(nodeCopy, succ);
            }
         }
      }

      return copies;
   }
}
