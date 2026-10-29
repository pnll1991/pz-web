package org.teavm.common;

import java.util.Arrays;
import org.teavm.hppc.IntArrayList;

class IrreducibleGraphSplitter {
   private GraphSplittingBackend backend;
   private DominatorTree dom;
   private int[][] domNodes;
   private MutableDirectedGraph cfg;
   private int[] weights;
   private IntArrayList[] realNodes;
   private int[][] spBackEdges;
   private int[] tmpArray;
   private int[] tmpArray2;
   private IntArrayList copiedRealNodes = new IntArrayList();
   private int additionalWeight;
   private int[] collapseMap;

   IrreducibleGraphSplitter(GraphSplittingBackend backend, Graph src, int[] weights) {
      this(backend, src, weights, initRealNodes(src.size()));
   }

   private static int[][] initRealNodes(int size) {
      int[][] result = new int[size][];

      for (int i = 0; i < size; i++) {
         result[i] = new int[]{i};
      }

      return result;
   }

   private IrreducibleGraphSplitter(GraphSplittingBackend backend, Graph src, int[] weights, int[][] realNodes) {
      int size = src.size();
      if (size == weights.length && size == realNodes.length) {
         this.backend = backend;
         this.tmpArray = new int[src.size()];
         this.tmpArray2 = new int[src.size()];
         this.cfg = new MutableDirectedGraph(src);
         this.dom = GraphUtils.buildDominatorTree(src);
         this.collapseMap = new int[size];
         int i = 0;

         while (i < size) {
            this.collapseMap[i] = i++;
         }

         this.buildDomGraph();
         this.dfs();
         this.realNodes = new IntArrayList[realNodes.length];

         for (int ix = 0; ix < this.cfg.size(); ix++) {
            this.realNodes[ix] = IntArrayList.from(realNodes[ix]);
         }

         this.weights = (int[])weights.clone();
      } else {
         throw new IllegalArgumentException("Node count " + src.size() + " is not equal to weight array " + weights.length);
      }
   }

   private void buildDomGraph() {
      int size = this.cfg.size();
      int[] domGraphCount = new int[size];

      for (int i = 0; i < size; i++) {
         int j = this.dom.immediateDominatorOf(i);
         if (j >= 0) {
            domGraphCount[j]++;
         }
      }

      int[][] domGraph = new int[size][];

      for (int i = 0; i < size; i++) {
         domGraph[i] = new int[domGraphCount[i]];
         domGraphCount[i] = 0;
      }

      for (int i = 0; i < size; i++) {
         int j = this.dom.immediateDominatorOf(i);
         if (j >= 0) {
            domGraph[j][domGraphCount[j]++] = i;
         }
      }

      this.domNodes = domGraph;
   }

   private void dfs() {
      int size = this.cfg.size();
      this.spBackEdges = new int[size][];
      int[] spBackEdgeCount = new int[size];

      for (int i = 0; i < size; i++) {
         int count = this.cfg.incomingEdgesCount(i);
         if (count > 0) {
            this.spBackEdges[i] = new int[this.cfg.incomingEdgesCount(i)];
         }
      }

      int[] state = new int[size];
      int[] stack = new int[size * 2];
      int top = 0;
      stack[top++] = 0;

      while (top > 0) {
         int node = stack[--top];
         switch (state[node]) {
            case 0:
               state[node] = 1;
               stack[top++] = node;

               for (int successor : this.cfg.outgoingEdges(node)) {
                  if (state[successor] == 0) {
                     stack[top++] = successor;
                  } else if (state[successor] == 1) {
                     this.spBackEdges[successor][spBackEdgeCount[successor]++] = node;
                  }
               }
               break;
            case 1:
               state[node] = 2;
         }
      }

      for (int i = 0; i < size; i++) {
         int[] back = this.spBackEdges[i];
         if (back != null) {
            int count = spBackEdgeCount[i];
            if (count == 0) {
               this.spBackEdges[i] = null;
            } else if (count < this.spBackEdges[i].length) {
               this.spBackEdges[i] = Arrays.copyOf(back, count);
            }
         }
      }
   }

   void splitLoops() {
      int size = this.cfg.size();
      boolean[] cross = new boolean[size];
      int[] stack = new int[size * 4];
      int head = 0;
      stack[head++] = 0;
      stack[head++] = 0;

      while (head > 0) {
         int state = stack[--head];
         int node = stack[--head];
         if (state == 0) {
            stack[head++] = node;
            stack[head++] = 1;
            int[] successors = this.domNodes[node];

            for (int i = successors.length - 1; i >= 0; i--) {
               stack[head++] = successors[i];
               stack[head++] = 0;
            }
         } else {
            if (cross[node]) {
               this.handleIrreducibleChildren(node);
            }

            int[] back = this.spBackEdges[node];
            int parent = this.dom.immediateDominatorOf(node);
            if (back != null && parent >= 0) {
               for (int predecessor : back) {
                  if (!this.dom.dominates(node, predecessor)) {
                     cross[parent] = true;
                     break;
                  }
               }
            }

            if (this.domNodes[node].length > 1) {
               this.collapse(node);
            }
         }
      }
   }

   private void handleIrreducibleChildren(int top) {
      Graph levelSubgraph = GraphUtils.subgraph(this.cfg, node -> node != top && this.dom.dominates(top, node));
      int[][] sccs = GraphUtils.findStronglyConnectedComponents(levelSubgraph);

      for (int[] scc : sccs) {
         if (scc.length > 1) {
            this.handleStronglyConnectedComponent(top, scc);
         }
      }
   }

   private void handleStronglyConnectedComponent(int top, int[] scc) {
      int domainCount = 0;

      for (int node : scc) {
         if (this.dom.immediateDominatorOf(node) == top) {
            domainCount++;
         }
      }

      if (domainCount >= 2) {
         int[] domains = this.fillDomains(top, scc);
         int[] localWeights = this.fillWeights(domains, scc);
         int remaining = -1;
         int maxWeight = 0;

         for (int node : scc) {
            if (domains[node] == node && (remaining < 0 || localWeights[node] > maxWeight)) {
               maxWeight = localWeights[node];
               remaining = node;
            }
         }

         int realRemainingNodesCount = 0;
         int realNodesToCopyCount = 0;
         int copyWeight = 0;
         int remainingNodeCount = 0;

         for (int node : scc) {
            if (domains[node] != remaining) {
               realNodesToCopyCount += this.realNodes[node].size();
               copyWeight += this.weights[node];
            } else {
               remainingNodeCount++;
               realRemainingNodesCount += this.realNodes[node].size();
            }
         }

         int[] realNodesToCopy = new int[realNodesToCopyCount];
         int[] realRemainingNodes = new int[realRemainingNodesCount];
         realNodesToCopyCount = 0;
         realRemainingNodesCount = 0;

         for (int node : scc) {
            int[] nodes = this.realNodes[node].toArray();
            if (domains[node] != remaining) {
               System.arraycopy(nodes, 0, realNodesToCopy, realNodesToCopyCount, nodes.length);
               realNodesToCopyCount += nodes.length;
            } else {
               System.arraycopy(nodes, 0, realRemainingNodes, realRemainingNodesCount, nodes.length);
               realRemainingNodesCount += nodes.length;
            }
         }

         int[] realNodesCopies = this.backend.split(realRemainingNodes, realNodesToCopy);
         this.copiedRealNodes.add(realNodesCopies);
         int subgraphSize = scc.length * 2 + 1 - remainingNodeCount;
         GraphBuilder subgraph = new GraphBuilder(subgraphSize);
         int[][] subgraphRealNodes = new int[subgraphSize][];
         int[] subgraphWeights = new int[subgraphSize];
         int[] map = new int[this.cfg.size()];
         int[] copyMap = new int[this.cfg.size()];
         Arrays.fill(map, -1);
         Arrays.fill(copyMap, -1);
         subgraphRealNodes[0] = new int[0];
         subgraphWeights[0] = 0;

         for (int i = 0; i < scc.length; i++) {
            int node = scc[i];
            map[node] = i + 1;
            subgraphRealNodes[i + 1] = this.realNodes[node].toArray();
            subgraphWeights[i + 1] = this.weights[node];
            if (domains[node] == node) {
               boolean hasPredecessorsOutsideLoop = false;

               for (int pred : this.cfg.incomingEdges(node)) {
                  if (domains[pred] < 0) {
                     hasPredecessorsOutsideLoop = true;
                     break;
                  }
               }

               if (hasPredecessorsOutsideLoop) {
                  subgraph.addEdge(0, i + 1);
               }
            }
         }

         int copyIndex = scc.length + 1;
         int realNodeCopiesIndex = 0;

         for (int node : scc) {
            if (domains[node] != remaining) {
               copyMap[node] = copyIndex;
               int realNodeCount = this.realNodes[node].size();
               subgraphRealNodes[copyIndex] = Arrays.copyOfRange(realNodesCopies, realNodeCopiesIndex, realNodeCopiesIndex + realNodeCount);
               realNodeCopiesIndex += realNodeCount;
               subgraphWeights[copyIndex] = this.weights[node];
               copyIndex++;
            }
         }

         for (int node : scc) {
            int subgraphNode = map[node];
            int subgraphNodeCopy = copyMap[node];
            int[] successors = this.cfg.outgoingEdges(node);

            for (int successor : successors) {
               int subgraphSuccessor = map[successor];
               int subgraphSuccessorCopy = copyMap[successor];
               if (subgraphSuccessorCopy >= 0) {
                  if (subgraphNodeCopy >= 0) {
                     subgraph.addEdge(subgraphNodeCopy, subgraphSuccessorCopy);
                     if (subgraphSuccessor >= 0) {
                        subgraph.addEdge(subgraphNode, subgraphSuccessor);
                     }
                  } else {
                     subgraph.addEdge(subgraphNode, subgraphSuccessorCopy);
                  }
               } else if (subgraphSuccessor >= 0) {
                  if (subgraphNodeCopy >= 0) {
                     subgraph.addEdge(subgraphNodeCopy, subgraphSuccessor);
                  }

                  subgraph.addEdge(subgraphNode, subgraphSuccessor);
               }
            }
         }

         IrreducibleGraphSplitter subgraphSplitter = new IrreducibleGraphSplitter(this.backend, subgraph.build(), subgraphWeights, subgraphRealNodes);
         subgraphSplitter.splitLoops();
         this.realNodes[top].addAll(subgraphSplitter.copiedRealNodes);
         this.realNodes[top].add(realNodesCopies);
         this.weights[top] = this.weights[top] + subgraphSplitter.additionalWeight + copyWeight;
         this.copiedRealNodes.addAll(subgraphSplitter.copiedRealNodes);
         this.additionalWeight = this.additionalWeight + subgraphSplitter.additionalWeight + copyWeight;
      }
   }

   private int[] fillDomains(int top, int[] nodes) {
      int[] domains = this.tmpArray;
      Arrays.fill(domains, -1);

      for (int node : nodes) {
         int domain = domains[node];
         if (domain < 0) {
            while (true) {
               int parent = this.dom.immediateDominatorOf(node);
               if (parent == top) {
                  domain = node;
                  break;
               }

               domain = domains[parent];
               if (domain >= 0) {
                  break;
               }

               domains[parent] = node;
               node = parent;
            }

            while (true) {
               int next = domains[node];
               domains[node] = domain;
               if (next == -1) {
                  break;
               }

               node = next;
            }
         }
      }

      return domains;
   }

   private int[] fillWeights(int[] domains, int[] nodes) {
      int[] localWeights = this.tmpArray2;

      for (int node : nodes) {
         if (domains[node] == node) {
            localWeights[node] = 0;
         }
      }

      for (int node : nodes) {
         localWeights[domains[node]] = localWeights[domains[node]] + this.weights[node];
      }

      return localWeights;
   }

   private void collapse(int top) {
      if (this.domNodes[top] != null && this.domNodes[top].length != 0) {
         int count = this.findAllDominatedNodes(top);
         int[] nodes = this.tmpArray;
         IntArrayList topRealNodes = this.realNodes[top];

         for (int i = 1; i < count; i++) {
            int node = nodes[i];
            topRealNodes.addAll(this.realNodes[node]);
            this.realNodes[node] = null;
            this.weights[top] = this.weights[top] + this.weights[node];
            this.collapseMap[node] = top;
         }

         for (int i = 1; i < count; i++) {
            int node = nodes[i];

            for (int succ : this.cfg.outgoingEdges(node)) {
               int mappedSucc = this.collapseMap[succ];
               if (mappedSucc != top || succ == top) {
                  this.cfg.addEdge(top, mappedSucc);
               }
            }

            for (int pred : this.cfg.incomingEdges(node)) {
               int mappedPred = this.collapseMap[pred];
               if (mappedPred != top) {
                  this.cfg.addEdge(mappedPred, top);
               }
            }

            this.cfg.detachNode(node);
         }

         this.domNodes[top] = null;
      }
   }

   private int findAllDominatedNodes(int top) {
      int[] result = this.tmpArray;
      int count = 0;
      int head = 0;

      for (result[count++] = top; head < count; head++) {
         int[] successors = this.domNodes[result[head]];
         if (successors != null && successors.length > 0) {
            System.arraycopy(successors, 0, result, count, successors.length);
            count += successors.length;
         }
      }

      return count;
   }
}
