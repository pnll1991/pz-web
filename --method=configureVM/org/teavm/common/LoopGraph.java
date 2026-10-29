package org.teavm.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class LoopGraph implements Graph {
   private Graph graph;
   private LoopGraph.LoopImpl[] loops;
   private LoopGraph.LoopImpl[] loopSet;
   private int[] walkIndexes;

   public LoopGraph(Graph graph) {
      this.graph = graph;
      this.findLoops();
   }

   private void findLoops() {
      int sz = this.graph.size();
      this.loops = new LoopGraph.LoopImpl[sz];
      this.loopSet = new LoopGraph.LoopImpl[sz];
      this.walkIndexes = new int[sz];
      LoopGraph.LoopImpl[] createdLoops = new LoopGraph.LoopImpl[sz];
      LoopGraph.LoopFrame[] frames = new LoopGraph.LoopFrame[sz];
      Deque<LoopGraph.LoopFrame> stack = new ArrayDeque<>(sz * 4);
      LoopGraph.LoopFrame rootFrame = new LoopGraph.LoopFrame();
      stack.push(rootFrame);
      int walkIndex = 0;
      int lastSortIndex = sz - 1;
      int loopSetSize = 0;

      while (!stack.isEmpty()) {
         LoopGraph.LoopFrame frame = stack.pop();
         if (frames[frame.index] == null) {
            frames[frame.index] = frame;
            frame.walkIndex = walkIndex++;
            stack.push(frame);
            int[] targetEdges = this.graph.outgoingEdges(frame.index);

            for (int i = 0; i < targetEdges.length; i++) {
               int next = targetEdges[i];
               LoopGraph.LoopFrame nextFrame = frames[next];
               if (nextFrame == null) {
                  nextFrame = new LoopGraph.LoopFrame();
                  nextFrame.index = next;
                  stack.push(nextFrame);
               }
            }
         } else {
            frame.sortIndex = lastSortIndex--;
            frame.done = true;
            LoopGraph.LoopImpl bestLoop = null;
            int[] targetEdges = this.graph.outgoingEdges(frame.index);

            for (int i = 0; i < targetEdges.length; i++) {
               int next = targetEdges[i];
               LoopGraph.LoopFrame nextFrame = frames[next];
               LoopGraph.LoopImpl loop = nextFrame.loop;
               if (!nextFrame.done) {
                  loop = createdLoops[next];
                  if (loop == null) {
                     loop = new LoopGraph.LoopImpl();
                     loop.head = next;
                     loop.walkIndex = nextFrame.walkIndex;
                     createdLoops[next] = loop;
                     this.loopSet[loopSetSize++] = loop;
                  }
               } else {
                  while (loop != null && loop.head == next) {
                     loop = loop.parent;
                  }
               }

               if (loop != null) {
                  bestLoop = this.chooseLoop(bestLoop, loop);
               }
            }

            frame.loop = bestLoop;
         }
      }

      this.loopSet = Arrays.copyOf(this.loopSet, loopSetSize);

      for (int i = 0; i < frames.length; i++) {
         this.loops[i] = frames[i] != null ? frames[i].loop : null;
         this.walkIndexes[i] = frames[i] != null ? frames[i].sortIndex : -1;
      }
   }

   public static Loop commonSuperloop(Loop first, Loop second) {
      if (first == second) {
         return first;
      }

      List<Loop> firstPath = new ArrayList<>();
      List<Loop> secondPath = new ArrayList<>();

      while (first != null) {
         firstPath.add(first);
         first = first.getParent();
      }

      firstPath.add(null);

      while (second != null) {
         secondPath.add(second);
         second = second.getParent();
      }

      secondPath.add(null);
      Collections.reverse(firstPath);
      Collections.reverse(secondPath);
      int sz = Math.min(firstPath.size(), secondPath.size());

      for (int i = 1; i < sz; i++) {
         if (firstPath.get(i) != secondPath.get(i)) {
            return firstPath.get(i - 1);
         }
      }

      return firstPath.get(sz - 1);
   }

   private LoopGraph.LoopImpl chooseLoop(LoopGraph.LoopImpl bestLoop, LoopGraph.LoopImpl testLoop) {
      if (bestLoop == null || bestLoop == testLoop) {
         return testLoop;
      }

      if (bestLoop.walkIndex < testLoop.walkIndex) {
         testLoop.parent = bestLoop;
         return testLoop;
      }

      LoopGraph.LoopImpl loop;
      for (loop = bestLoop; loop.getParent() != null; loop = loop.parent) {
         if (loop == testLoop) {
            return bestLoop;
         }

         if (loop.parent.walkIndex < testLoop.walkIndex) {
            testLoop.parent = loop.parent;
            loop.parent = testLoop;
            break;
         }
      }

      if (loop.parent == null && loop != testLoop) {
         loop.parent = testLoop;
      }

      return bestLoop;
   }

   public Loop loopAt(int index) {
      return this.loops[index];
   }

   public Loop[] knownLoops() {
      return Arrays.copyOf(this.loopSet, this.loopSet.length);
   }

   @Override
   public int size() {
      return this.graph.size();
   }

   @Override
   public int[] incomingEdges(int node) {
      return this.graph.incomingEdges(node);
   }

   @Override
   public int copyIncomingEdges(int node, int[] target) {
      return this.graph.copyIncomingEdges(node, target);
   }

   @Override
   public int[] outgoingEdges(int node) {
      return this.graph.outgoingEdges(node);
   }

   @Override
   public int copyOutgoingEdges(int node, int[] target) {
      return this.graph.copyOutgoingEdges(node, target);
   }

   @Override
   public int incomingEdgesCount(int node) {
      return this.graph.incomingEdgesCount(node);
   }

   @Override
   public int outgoingEdgesCount(int node) {
      return this.graph.outgoingEdgesCount(node);
   }

   public int indexAt(int node) {
      return this.walkIndexes[node];
   }

   private static class LoopFrame {
      public int walkIndex;
      public int index;
      public int sortIndex;
      public LoopGraph.LoopImpl loop;
      public boolean done;
   }

   private static class LoopImpl implements Loop {
      public int head;
      public LoopGraph.LoopImpl parent;
      public int walkIndex;

      @Override
      public int getHead() {
         return this.head;
      }

      @Override
      public Loop getParent() {
         return this.parent;
      }

      @Override
      public boolean isChildOf(Loop other) {
         if (other == null) {
            return true;
         }

         for (Loop loop = this; loop != null; loop = loop.getParent()) {
            if (loop == other) {
               return true;
            }
         }

         return false;
      }
   }
}
