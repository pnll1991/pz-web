package org.teavm.common;

import java.util.Arrays;

class DominatorTreeBuilder {
   private Graph graph;
   private int[] semidominators;
   int[] vertices;
   private int[] parents;
   private int[] ancestors;
   private int[] labels;
   int[] dominators;
   private IntegerArray[] bucket;
   private int effectiveSize;
   private int start;

   DominatorTreeBuilder(Graph graph, int start) {
      this.graph = graph;
      this.start = start;
      this.semidominators = new int[graph.size()];
      this.vertices = new int[graph.size()];
      this.parents = new int[graph.size()];
      this.dominators = new int[graph.size()];
      Arrays.fill(this.dominators, -1);
      this.ancestors = new int[graph.size()];
      this.labels = new int[graph.size()];
      this.bucket = new IntegerArray[graph.size()];
   }

   void build() {
      int i = 0;

      while (i < this.labels.length) {
         this.labels[i] = i++;
      }

      Arrays.fill(this.ancestors, -1);
      this.dfs();

      for (int ix = this.effectiveSize - 1; ix > 0; ix--) {
         int w = this.vertices[ix];

         for (int v : this.graph.incomingEdges(w)) {
            int u = this.eval(v);
            this.semidominators[w] = Math.min(this.semidominators[w], this.semidominators[u]);
         }

         this.addToBucket(this.vertices[this.semidominators[w]], w);
         this.link(this.parents[w], w);

         for (int v : this.getBucket(this.parents[w])) {
            int u = this.eval(v);
            this.dominators[v] = this.semidominators[u] < this.semidominators[v] ? u : this.parents[w];
         }

         this.bucket[w] = null;
      }

      for (int ix = 1; ix < this.effectiveSize; ix++) {
         int w = this.vertices[ix];
         if (this.dominators[w] != this.vertices[this.semidominators[w]]) {
            this.dominators[w] = this.dominators[this.dominators[w]];
         }
      }

      this.dominators[this.start] = -1;
   }

   private void addToBucket(int v, int w) {
      IntegerArray ws = this.bucket[v];
      if (ws == null) {
         ws = new IntegerArray(1);
         this.bucket[v] = ws;
      }

      ws.add(w);
   }

   private int[] getBucket(int v) {
      IntegerArray ws = this.bucket[v];
      return ws != null ? ws.getAll() : new int[0];
   }

   private void link(int v, int w) {
      this.ancestors[w] = v;
   }

   private int eval(int v) {
      int ancestor = this.ancestors[v];
      if (ancestor < 0) {
         return v;
      }

      this.compress(v);
      return this.labels[v];
   }

   private void compress(int v) {
      if (this.ancestors[this.ancestors[v]] >= 0) {
         this.compress(this.ancestors[v]);
         if (this.semidominators[this.labels[this.ancestors[v]]] < this.semidominators[this.labels[v]]) {
            this.labels[v] = this.labels[this.ancestors[v]];
         }

         this.ancestors[v] = this.ancestors[this.ancestors[v]];
      }
   }

   private void dfs() {
      Arrays.fill(this.semidominators, -1);
      Arrays.fill(this.vertices, -1);
      IntegerStack stack = new IntegerStack(this.graph.size());
      stack.push(this.start);
      Arrays.fill(this.parents, -1);
      int i = 0;

      while (!stack.isEmpty()) {
         int v = stack.pop();
         if (this.semidominators[v] < 0) {
            this.dominators[v] = this.start;
            this.semidominators[v] = i;
            this.vertices[i++] = v;

            for (int w : this.graph.outgoingEdges(v)) {
               if (this.semidominators[w] < 0) {
                  this.parents[w] = v;
                  stack.push(w);
               }
            }
         }
      }

      this.effectiveSize = i;
      this.dominators[this.start] = this.start;
   }
}
