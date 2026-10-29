package org.teavm.common;

import java.util.Arrays;

public class DisjointSet {
   private int[] parent = new int[16];
   private int[] rank = new int[16];
   private int[] setSize = new int[16];
   private int maxRank;
   private int size;

   public int find(int node) {
      int initial = node;

      int depth;
      for (depth = 0; this.parent[node] != node; depth++) {
         node = this.parent[node];
      }

      if (depth > 1) {
         this.parent[initial] = node;
      }

      return node;
   }

   public int[] pack(int count) {
      int[] packed = new int[count];
      int[] packedIndexes = new int[this.size];
      Arrays.fill(packedIndexes, -1);
      int lastIndex = 0;

      for (int i = 0; i < count; i++) {
         int cls = this.find(i);
         int packedIndex = packedIndexes[cls];
         if (packedIndex < 0) {
            packedIndex = lastIndex++;
            packedIndexes[cls] = packedIndex;
         }

         packed[i] = packedIndex;
      }

      return packed;
   }

   private void ensureCapacity(int size) {
      if (this.parent.length < size) {
         int newCap = size * 3 / 2 + 1;
         this.parent = Arrays.copyOf(this.parent, newCap);
         this.rank = Arrays.copyOf(this.rank, newCap);
         this.setSize = Arrays.copyOf(this.setSize, newCap);
      }
   }

   public int create() {
      int node = this.size++;
      this.ensureCapacity(this.size);
      this.parent[node] = node;
      this.rank[node] = 1;
      this.setSize[node] = 1;
      return node;
   }

   public int union(int a, int b) {
      a = this.find(a);
      b = this.find(b);
      if (a == b) {
         return a;
      } else if (this.rank[a] > this.rank[b]) {
         this.parent[b] = a;
         this.setSize[a] = this.setSize[a] + this.setSize[b];
         return a;
      } else if (this.rank[b] > this.rank[a]) {
         this.parent[a] = b;
         this.setSize[b] = this.setSize[b] + this.setSize[a];
         return b;
      } else {
         this.parent[b] = a;
         this.setSize[a] = this.setSize[a] + this.setSize[b];
         this.rank[a]++;
         this.maxRank = Math.max(this.maxRank, this.rank[a]);
         return a;
      }
   }

   public int sizeOf(int node) {
      return this.setSize[node];
   }

   public int size() {
      return this.size;
   }
}
