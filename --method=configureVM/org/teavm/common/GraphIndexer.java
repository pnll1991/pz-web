package org.teavm.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;

public class GraphIndexer {
   static final byte NONE = 0;
   static final byte VISITING = 1;
   static final byte VISITED = 2;
   private int[] indexToNode;
   private int[] nodeToIndex;
   private Graph graph;
   private DominatorTree domTree;
   private int lastIndex;
   private int[] weights;
   private int[] priorities;

   public GraphIndexer(Graph graph, int[] weights, int[] priorities) {
      int sz = graph.size();
      this.weights = (int[])weights.clone();
      this.propagateWeights(graph, this.weights);
      this.priorities = (int[])priorities.clone();
      this.propagatePriorities(graph, this.priorities);
      this.indexToNode = new int[sz + 1];
      this.nodeToIndex = new int[sz + 1];
      Arrays.fill(this.nodeToIndex, -1);
      Arrays.fill(this.indexToNode, -1);
      this.graph = graph;
      this.domTree = GraphUtils.buildDominatorTree(graph);
      this.sort(graph);
      this.lastIndex--;

      for (int node = 0; node < sz; node++) {
         int index = this.nodeToIndex[node];
         if (index >= 0) {
            index = this.lastIndex - index;
            this.nodeToIndex[node] = index;
            this.indexToNode[index] = node;
         }
      }

      this.indexToNode[sz] = sz;
      this.nodeToIndex[sz] = sz;
      GraphBuilder sorted = new GraphBuilder(this.lastIndex + 2);

      for (int i = 0; i <= this.lastIndex; i++) {
         for (int next : graph.outgoingEdges(this.indexToNode[i])) {
            sorted.addEdge(i, this.nodeToIndex[next]);
         }
      }

      this.graph = sorted.build();
   }

   private void propagateWeights(Graph graph, int[] weights) {
      int sz = graph.size();
      byte[] state = new byte[sz];
      IntegerStack stack = new IntegerStack(sz * 2);
      stack.push(0);

      while (!stack.isEmpty()) {
         int node = stack.pop();
         switch (state[node]) {
            case 0:
               state[node] = 1;
               stack.push(node);

               for (int succ : graph.outgoingEdges(node)) {
                  if (state[succ] == 0) {
                     stack.push(succ);
                  }
               }
               break;
            case 1:
               state[node] = 2;

               for (int succ : graph.outgoingEdges(node)) {
                  if (state[succ] == 2) {
                     weights[node] += weights[succ];
                  }
               }
         }
      }
   }

   private void propagatePriorities(Graph graph, int[] priorities) {
      boolean allZero = true;

      for (int i = 0; i < priorities.length; i++) {
         if (priorities[i] != 0) {
            allZero = false;
            break;
         }
      }

      if (!allZero) {
         DominatorTree domTree = GraphUtils.buildDominatorTree(graph);
         Graph domGraph = GraphUtils.buildDominatorGraph(domTree, graph.size());
         IntegerStack stack = new IntegerStack(graph.size() * 2);

         for (int i = 0; i < domGraph.size(); i++) {
            if (domGraph.outgoingEdgesCount(i) == 0) {
               stack.push(i);
            }
         }

         while (!stack.isEmpty()) {
            int node = stack.pop();
            int parent = domTree.immediateDominatorOf(node);
            if (parent >= 0 && priorities[parent] < priorities[node]) {
               priorities[parent] = priorities[node];
               stack.push(parent);
            }
         }
      }
   }

   private void sort(Graph graph) {
      int sz = graph.size();
      byte[] state = new byte[sz];
      IntegerStack stack = new IntegerStack(sz * 2);
      stack.push(0);

      while (!stack.isEmpty()) {
         int node = stack.pop();
         switch (state[node]) {
            case 0:
               state[node] = 1;
               stack.push(node);
               IntegerArray terminalNodes = new IntegerArray(1);

               for (int pred : graph.incomingEdges(node)) {
                  if (this.domTree.dominates(node, pred)) {
                     terminalNodes.add(pred);
                  }
               }

               int[] successors = graph.outgoingEdges(node);
               List<GraphIndexer.WeightedNode> succList = new ArrayList<>(successors.length);
               IntegerArray orderedSuccessors = new IntegerArray(successors.length);
               if (terminalNodes.size() > 0) {
                  int[] loopNodeList = this.findNaturalLoop(node, terminalNodes.getAll());
                  IntSet loopNodes = IntHashSet.from(loopNodeList);

                  for (int succ : successors) {
                     if (loopNodes.contains(succ)) {
                        succList.add(new GraphIndexer.WeightedNode(succ, this.priorities[succ], this.weights[succ]));
                     }
                  }

                  Collections.sort(succList);

                  for (GraphIndexer.WeightedNode wnode : succList) {
                     orderedSuccessors.add(wnode.index);
                  }

                  IntSet outerSuccessors = new IntHashSet(successors.length);
                  succList.clear();

                  for (int loopNode : loopNodeList) {
                     for (int succ : graph.outgoingEdges(loopNode)) {
                        if (!loopNodes.contains(succ) && outerSuccessors.add(succ)) {
                           succList.add(new GraphIndexer.WeightedNode(succ, this.priorities[succ], this.weights[succ]));
                        }
                     }
                  }

                  Collections.sort(succList);

                  for (GraphIndexer.WeightedNode wnode : succList) {
                     orderedSuccessors.add(wnode.index);
                  }
               } else {
                  for (int succ : successors) {
                     succList.add(new GraphIndexer.WeightedNode(succ, this.priorities[succ], this.weights[succ]));
                  }

                  Collections.sort(succList);

                  for (GraphIndexer.WeightedNode wnode : succList) {
                     orderedSuccessors.add(wnode.index);
                  }
               }

               successors = orderedSuccessors.getAll();

               for (int succ : successors) {
                  if (state[succ] == 0) {
                     stack.push(succ);
                  }
               }
               break;
            case 1:
               state[node] = 2;
               this.nodeToIndex[node] = this.lastIndex++;
         }
      }
   }

   private int[] findNaturalLoop(int head, int[] terminals) {
      IntSet loop = new IntHashSet();
      IntArrayList loopList = new IntArrayList();
      loop.add(head);
      loopList.add(head);
      IntegerStack stack = new IntegerStack(1);

      for (int pred : terminals) {
         stack.push(pred);
      }

      while (!stack.isEmpty()) {
         int node = stack.pop();
         if (loop.add(node)) {
            loopList.add(node);

            for (int pred : this.graph.incomingEdges(node)) {
               stack.push(pred);
            }
         }
      }

      return loopList.toArray();
   }

   public int nodeAt(int index) {
      return this.indexToNode[index];
   }

   public int indexOf(int node) {
      return this.nodeToIndex[node];
   }

   public int size() {
      return this.indexToNode.length - 1;
   }

   public Graph getGraph() {
      return this.graph;
   }

   static class WeightedNode implements Comparable<GraphIndexer.WeightedNode> {
      int index;
      int priority;
      int weight;

      public WeightedNode(int index, int priority, int weight) {
         this.index = index;
         this.priority = priority;
         this.weight = weight;
      }

      public int compareTo(GraphIndexer.WeightedNode o) {
         int r = Integer.compare(this.priority, o.priority);
         return r != 0 ? r : Integer.compare(this.weight, o.weight);
      }
   }
}
