package org.teavm.common;

import java.util.ArrayList;
import java.util.List;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.cursors.IntCursor;

public class MutableDirectedGraph implements Graph {
   private List<MutableDirectedGraph.NodeSet> successors = new ArrayList<>();
   private List<MutableDirectedGraph.NodeSet> predecessors = new ArrayList<>();

   public MutableDirectedGraph() {
   }

   public MutableDirectedGraph(Graph graph) {
      int[] data = new int[graph.size()];

      for (int i = 0; i < graph.size(); i++) {
         int sz = graph.copyOutgoingEdges(i, data);

         for (int j = 0; j < sz; j++) {
            this.addEdge(i, data[j]);
         }
      }

      while (this.successors.size() < graph.size()) {
         this.successors.add(new MutableDirectedGraph.NodeSet());
         this.predecessors.add(new MutableDirectedGraph.NodeSet());
      }
   }

   public Graph copyToImmutable() {
      GraphBuilder builder = new GraphBuilder(this.successors.size());

      for (int i = 0; i < this.successors.size(); i++) {
         for (IntCursor successor : this.successors.get(i).list) {
            builder.addEdge(i, successor.value);
         }
      }

      return builder.build();
   }

   public int addNode() {
      int index = this.successors.size();
      this.successors.add(new MutableDirectedGraph.NodeSet());
      this.predecessors.add(new MutableDirectedGraph.NodeSet());
      return index;
   }

   @Override
   public int size() {
      return this.successors.size();
   }

   public void addEdge(int from, int to) {
      int max = Math.max(from, to);

      while (max >= this.successors.size()) {
         this.successors.add(new MutableDirectedGraph.NodeSet());
         this.predecessors.add(new MutableDirectedGraph.NodeSet());
      }

      MutableDirectedGraph.NodeSet successorNodes = this.successors.get(from);
      if (successorNodes.set.add(to)) {
         successorNodes.list.add(to);
         MutableDirectedGraph.NodeSet predecessorNodes = this.predecessors.get(to);
         predecessorNodes.set.add(from);
         predecessorNodes.list.add(from);
      }
   }

   public void deleteEdge(int from, int to) {
      if (from < this.successors.size() && to < this.successors.size()) {
         MutableDirectedGraph.NodeSet successorNodes = this.successors.get(from);
         if (successorNodes.set.removeAll(to) > 0) {
            successorNodes.list.removeAll(to);
            MutableDirectedGraph.NodeSet predecessorNodes = this.predecessors.get(to);
            predecessorNodes.set.removeAll(from);
            predecessorNodes.list.removeAll(from);
         }
      }
   }

   public void detachNode(int node) {
      for (IntCursor succ : this.successors.get(node).list) {
         MutableDirectedGraph.NodeSet predecessorNodes = this.predecessors.get(succ.value);
         predecessorNodes.set.removeAll(node);
         predecessorNodes.list.removeAll(node);
      }

      for (IntCursor pred : this.predecessors.get(node).list) {
         MutableDirectedGraph.NodeSet successorNodes = this.successors.get(pred.value);
         successorNodes.set.removeAll(node);
         successorNodes.list.removeAll(node);
      }

      MutableDirectedGraph.NodeSet predecessorNodes = this.predecessors.get(node);
      predecessorNodes.list.clear();
      predecessorNodes.set.clear();
      MutableDirectedGraph.NodeSet successorNodes = this.successors.get(node);
      successorNodes.list.clear();
      predecessorNodes.list.clear();
   }

   @Override
   public int[] incomingEdges(int node) {
      return this.predecessors.get(node).list.toArray();
   }

   @Override
   public int copyIncomingEdges(int node, int[] target) {
      int index = 0;

      for (IntCursor cursor : this.predecessors.get(node).list) {
         target[index++] = cursor.value;
      }

      return index;
   }

   @Override
   public int[] outgoingEdges(int node) {
      return this.successors.get(node).list.toArray();
   }

   @Override
   public int copyOutgoingEdges(int node, int[] target) {
      int index = 0;

      for (IntCursor cursor : this.successors.get(node).list) {
         target[index++] = cursor.value;
      }

      return index;
   }

   @Override
   public int incomingEdgesCount(int node) {
      return this.predecessors.get(node).list.size();
   }

   @Override
   public int outgoingEdgesCount(int node) {
      return this.successors.get(node).list.size();
   }

   @Override
   public String toString() {
      return GraphUtils.printToDot(this);
   }

   static class NodeSet {
      IntHashSet set = new IntHashSet(1);
      IntArrayList list = new IntArrayList(1);
   }
}
