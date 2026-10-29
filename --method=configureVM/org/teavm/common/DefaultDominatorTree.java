package org.teavm.common;

import java.util.Arrays;

class DefaultDominatorTree implements DominatorTree {
   private LCATree lcaTree;
   private int[] indexes;
   private int[] nodes;

   public DefaultDominatorTree(int[] dominators, int[] vertices) {
      this.lcaTree = new LCATree(dominators.length + 1);
      this.indexes = new int[dominators.length + 1];
      this.nodes = new int[dominators.length + 1];
      Arrays.fill(this.nodes, -1);

      for (int i = 0; i < dominators.length; i++) {
         int v = vertices[i];
         if (v >= 0) {
            int dom = this.indexes[dominators[v] + 1];
            int node = this.lcaTree.addNode(dom);
            this.indexes[v + 1] = node;
            this.nodes[node] = v;
         }
      }
   }

   @Override
   public boolean directlyDominates(int a, int b) {
      a = this.indexes[a + 1];
      b = this.indexes[b + 1];
      return this.lcaTree.lcaOf(a, b) == a;
   }

   @Override
   public int commonDominatorOf(int a, int b) {
      return this.nodes[this.lcaTree.lcaOf(this.indexes[a + 1], this.indexes[b + 1])];
   }

   @Override
   public boolean dominates(int a, int b) {
      a = this.indexes[a + 1];
      b = this.indexes[b + 1];
      return this.lcaTree.lcaOf(a, b) == a;
   }

   @Override
   public int immediateDominatorOf(int a) {
      int result = this.lcaTree.parentOf(this.indexes[a + 1]);
      return result >= 0 ? this.nodes[result] : -1;
   }

   @Override
   public int levelOf(int a) {
      int index = this.indexes[a + 1];
      return this.lcaTree.depthOf(index);
   }
}
