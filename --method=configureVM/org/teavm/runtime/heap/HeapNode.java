package org.teavm.runtime.heap;

import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
class HeapNode extends HeapRecord {
   static final int FLAG_LIST = 1;
   HeapNode left;
   HeapNode right;
   HeapNode list;
   short height;
   short flags;

   static HeapNode balance(HeapNode node) {
      int factor = factor(node);
      if (factor == 2) {
         if (factor(node.right) < 0) {
            node.right = rotateRight(node.right);
         }

         return rotateLeft(node);
      } else if (factor == -2) {
         if (factor(node.left) > 0) {
            node.left = rotateLeft(node.left);
         }

         return rotateRight(node);
      } else {
         return node;
      }
   }

   private static int factor(HeapNode node) {
      return (node.right != null ? node.right.height : 0) - (node.left != null ? node.left.height : 0);
   }

   private static HeapNode rotateRight(HeapNode node) {
      HeapNode left = node.left;
      node.left = left.right;
      left.right = node;
      fix(node);
      fix(left);
      return left;
   }

   private static HeapNode rotateLeft(HeapNode node) {
      HeapNode right = node.right;
      node.right = right.left;
      right.left = node;
      fix(node);
      fix(right);
      return right;
   }

   static void fix(HeapNode node) {
      short leftHeight = node.right != null ? node.right.height : 0;
      short rightHeight = node.left != null ? node.left.height : 0;
      node.height = (short)(Math.max(leftHeight, rightHeight) + 1);
   }
}
