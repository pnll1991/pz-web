package org.teavm.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class RangeTree {
   private RangeTree.NodeImpl root = new RangeTree.NodeImpl();

   public RangeTree(int sz, Iterable<RangeTree.Range> ranges) {
      this.root.start = 0;
      this.root.end = sz;
      List<RangeTree.Range> rangeList = new ArrayList<>();

      for (RangeTree.Range range : ranges) {
         rangeList.add(range);
      }

      rangeList.sort((o1, o2) -> o1.right != o2.right ? o2.right - o1.right : o1.left - o2.left);
      Deque<RangeTree.NodeImpl> stack = new ArrayDeque<>();
      stack.push(this.root);
      RangeTree.Range lastRange = null;

      for (RangeTree.Range range : rangeList) {
         if (lastRange == null || lastRange.left != range.left || lastRange.right != range.right) {
            RangeTree.NodeImpl current = new RangeTree.NodeImpl();
            current.start = range.left;
            current.end = range.right;

            while (range.right <= stack.peek().start) {
               stack.pop();
            }

            RangeTree.NodeImpl parent = stack.peek();
            current.next = parent.firstChild;
            parent.firstChild = current;
            current.parent = parent;

            for (RangeTree.NodeImpl ancestor : stack) {
               if (ancestor.start <= current.start) {
                  break;
               }

               ancestor.start = current.start;
            }

            stack.push(current);
            lastRange = range;
         }
      }
   }

   public RangeTree.Node getRoot() {
      return this.root;
   }

   public interface Node {
      RangeTree.Node getParent();

      int getStart();

      int getEnd();

      RangeTree.Node getNext();

      RangeTree.Node getFirstChild();
   }

   private static class NodeImpl implements RangeTree.Node {
      public RangeTree.NodeImpl parent;
      public int start;
      public int end;
      public RangeTree.NodeImpl firstChild;
      public RangeTree.NodeImpl next;

      @Override
      public RangeTree.Node getParent() {
         return this.parent;
      }

      @Override
      public int getStart() {
         return this.start;
      }

      @Override
      public int getEnd() {
         return this.end;
      }

      @Override
      public RangeTree.Node getNext() {
         return this.next;
      }

      @Override
      public RangeTree.Node getFirstChild() {
         return this.firstChild;
      }
   }

   public static class Range {
      public final int left;
      public final int right;

      public Range(int left, int right) {
         this.left = left;
         this.right = right;
      }

      @Override
      public String toString() {
         return "[" + this.left + "; " + this.right + ")";
      }
   }
}
