package org.teavm.common;

public class LCATree {
   private int[] depths;
   private int[][] pathsToRoot;
   private int sz;

   public LCATree(int capacity) {
      this.depths = new int[capacity];
      this.pathsToRoot = new int[capacity][];
      this.sz = 1;
      this.depths[0] = 0;
      this.pathsToRoot[0] = new int[]{0};
   }

   public int size() {
      return this.sz;
   }

   public int addNode(int parent) {
      int node = this.sz++;
      int depth = this.depths[parent] + 1;
      this.depths[node] = depth;

      int h;
      for (h = 0; depth > 0; h++) {
         depth /= 2;
      }

      int[] path = new int[h];
      this.pathsToRoot[node] = path;
      path[0] = parent;

      for (int i = 1; i < h; i++) {
         parent = this.pathsToRoot[parent][i - 1];
         path[i] = parent;
      }

      return node;
   }

   public int parentOf(int node) {
      int[] path = this.pathsToRoot[node];
      return path.length > 0 ? path[0] : -1;
   }

   public int depthOf(int node) {
      return this.depths[node];
   }

   public int lcaOf(int a, int b) {
      if (a == b) {
         return a;
      }

      if (this.depths[a] < this.depths[b]) {
         int t = a;
         a = b;
         b = t;
      }

      if (this.depths[a] != this.depths[b]) {
         int h = this.depths[a] - this.depths[b];
         int diff = 1;
         int diffln = 0;

         while (diff <= h) {
            diffln++;
            diff *= 2;
         }

         diffln--;
         diff /= 2;

         while (h > 0) {
            h -= diff;
            a = this.pathsToRoot[a][diffln];

            while (diff > h) {
               diff /= 2;
               diffln--;
            }
         }
      }

      if (a == b) {
         return a;
      }

      do {
         int i = 0;

         for (int len = Math.min(this.pathsToRoot[a].length, this.pathsToRoot[b].length); i < len; i++) {
            int p = this.pathsToRoot[a][i];
            int q = this.pathsToRoot[b][i];
            if (p == q) {
               break;
            }
         }

         if (--i < 0) {
            return this.pathsToRoot[a][0];
         }

         a = this.pathsToRoot[a][i];
         b = this.pathsToRoot[b][i];
      } while (a != b);

      return a;
   }
}
