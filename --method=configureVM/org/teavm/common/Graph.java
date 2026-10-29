package org.teavm.common;

public interface Graph {
   int size();

   int[] incomingEdges(int var1);

   int copyIncomingEdges(int var1, int[] var2);

   int[] outgoingEdges(int var1);

   int copyOutgoingEdges(int var1, int[] var2);

   int incomingEdgesCount(int var1);

   int outgoingEdgesCount(int var1);
}
