package org.teavm.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;

public class GraphBuilder {
   private GraphBuilder.GraphImpl builtGraph;
   private List<IntSet> addedEdges = new ArrayList<>();
   private int sz;

   public GraphBuilder() {
   }

   public GraphBuilder(int sz) {
      this.addedEdges.addAll(Collections.nCopies(sz, null));
      this.sz = sz;
   }

   public void clear() {
      this.addedEdges.clear();
      this.sz = 0;
   }

   public void addEdge(int from, int to) {
      if (to >= 0 && from >= 0) {
         this.sz = Math.max(this.sz, Math.max(from, to) + 1);
         this.builtGraph = null;
         if (this.addedEdges.size() == from) {
            this.addedEdges.add(IntHashSet.from(new int[]{to}));
         } else if (this.addedEdges.size() <= from) {
            this.addedEdges.addAll(Collections.nCopies(from - this.addedEdges.size(), null));
            this.addedEdges.add(IntHashSet.from(new int[]{to}));
         } else {
            IntSet set = this.addedEdges.get(from);
            if (set == null) {
               this.addedEdges.set(from, IntHashSet.from(new int[]{to}));
            } else {
               set.add(to);
            }
         }
      } else {
         throw new IllegalArgumentException();
      }
   }

   public void removeEdge(int from, int to) {
      if (to < 0 || from < 0) {
         throw new IllegalArgumentException();
      }

      if (from < this.addedEdges.size() && to < this.addedEdges.size()) {
         this.addedEdges.get(from).removeAll(to);
      }
   }

   public Graph build() {
      if (this.builtGraph == null) {
         IntSet[] incomingEdges = new IntSet[this.sz];

         for (int i = 0; i < this.sz; i++) {
            incomingEdges[i] = new IntHashSet();
         }

         int[][] outgoingEdgeList = new int[this.sz][];

         for (int i = 0; i < this.addedEdges.size(); i++) {
            IntSet edgeList = this.addedEdges.get(i);
            outgoingEdgeList[i] = edgeList != null ? edgeList.toArray() : new int[0];
            Arrays.sort(outgoingEdgeList[i]);

            for (int j : outgoingEdgeList[i]) {
               incomingEdges[j].add(i);
            }
         }

         for (int i = this.addedEdges.size(); i < this.sz; i++) {
            outgoingEdgeList[i] = new int[0];
         }

         int[][] incomingEdgeList = new int[this.sz][];

         for (int i = 0; i < this.sz; i++) {
            incomingEdgeList[i] = incomingEdges[i].toArray();
            Arrays.sort(incomingEdgeList[i]);
         }

         this.builtGraph = new GraphBuilder.GraphImpl(incomingEdgeList, outgoingEdgeList);
      }

      return this.builtGraph;
   }

   private static final class GraphImpl implements Graph {
      private final int[][] incomingEdgeList;
      private final int[][] outgoingEdgeList;

      GraphImpl(int[][] incomingEdgeList, int[][] outgoingEdgeList) {
         this.incomingEdgeList = incomingEdgeList;
         this.outgoingEdgeList = outgoingEdgeList;
      }

      @Override
      public int size() {
         return this.incomingEdgeList.length;
      }

      @Override
      public int[] incomingEdges(int node) {
         int[] result = this.incomingEdgeList[node];
         return Arrays.copyOf(result, result.length);
      }

      @Override
      public int copyIncomingEdges(int node, int[] target) {
         int[] result = this.incomingEdgeList[node];
         System.arraycopy(result, 0, target, 0, result.length);
         return result.length;
      }

      @Override
      public int[] outgoingEdges(int node) {
         int[] result = this.outgoingEdgeList[node];
         return Arrays.copyOf(result, result.length);
      }

      @Override
      public int copyOutgoingEdges(int node, int[] target) {
         int[] result = this.outgoingEdgeList[node];
         System.arraycopy(result, 0, target, 0, result.length);
         return result.length;
      }

      @Override
      public int incomingEdgesCount(int node) {
         return this.incomingEdgeList[node].length;
      }

      @Override
      public int outgoingEdgesCount(int node) {
         return this.outgoingEdgeList[node].length;
      }

      @Override
      public String toString() {
         return GraphUtils.printToDot(this);
      }
   }
}
