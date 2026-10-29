package org.teavm.runtime.heap;

import org.teavm.interop.Address;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.interop.Intrinsified;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
public final class Heap {
   private static Address start;
   private static Address end;
   private static int currentSize;
   private static int maxSize;
   private static HeapNode root;

   private Heap() {
   }

   static void init(Address start, int minSize, int maxSize) {
      Heap.start = start;
      end = start.add(minSize);
      currentSize = minSize;
      Heap.maxSize = maxSize;
      root = (HeapNode)start.toStructure();
      root.size = minSize - Structure.sizeOf(HeapRecord.class);
      root.left = null;
      root.right = null;
      root.list = null;
      root.height = 1;
      root.flags = 0;
   }

   static Address getStart() {
      return start;
   }

   static Address getEnd() {
      return end;
   }

   static HeapNode getRoot() {
      return root;
   }

   static int getCurrentSize() {
      return currentSize;
   }

   static int getMaxSize() {
      return maxSize;
   }

   static void resetRoot() {
      root = null;
   }

   @Export(name = "teavm.malloc")
   public static Address alloc(int bytes) {
      bytes = alignSize(bytes);
      Address result = tryAlloc(bytes);
      if (result == null) {
         HeapNode last = lastRecord();
         int amountToGrow = !HeapRecord.isAllocated(last) ? bytes - HeapRecord.size(last) : bytes + Structure.sizeOf(HeapRecord.class);
         if (amountToGrow > 0 && tryExtend(amountToGrow, last)) {
            result = tryAlloc(bytes);
         }
      }

      return result;
   }

   @Export(name = "teavm.realloc")
   public static Address realloc(Address address, int newSize) {
      newSize = alignSize(newSize);
      HeapRecord record = HeapRecord.recordOf(address);
      int oldSize = HeapRecord.size(record);
      if (oldSize == newSize) {
         return address;
      } else if (newSize < oldSize) {
         shrink(record, oldSize, newSize);
         return address;
      } else {
         return expand(record, oldSize, newSize);
      }
   }

   private static int alignSize(int bytes) {
      bytes = (Integer.divideUnsigned(bytes - 1, Address.sizeOf()) + 1) * Address.sizeOf();
      return Math.max(bytes, Structure.sizeOf(HeapNode.class) - Structure.sizeOf(HeapRecord.class));
   }

   private static void shrink(HeapRecord record, int oldSize, int newSize) {
      HeapRecord oldNextRecord = HeapRecord.next(record);
      if (oldNextRecord.toAddress() != end && !HeapRecord.isAllocated(oldNextRecord)
         || oldSize - newSize >= Structure.sizeOf(HeapNode.class) - Structure.sizeOf(HeapRecord.class)) {
         record.size = newSize;
         HeapRecord newNextRecord = HeapRecord.next(record);
         newNextRecord.previousSize = newSize;
         if (oldNextRecord.toAddress() == end) {
            newNextRecord.size = oldNextRecord.size;
         } else if (HeapRecord.isAllocated(oldNextRecord)) {
            oldNextRecord.previousSize = newSize - oldSize - Structure.sizeOf(HeapRecord.class);
            newNextRecord.size = oldNextRecord.size;
         } else {
            HeapRecord nextNextRecord = HeapRecord.next(oldNextRecord);
            delete((HeapNode)oldNextRecord);
            newNextRecord.size = newSize - oldSize + HeapRecord.size(oldNextRecord);
            nextNextRecord.previousSize = newNextRecord.size;
         }

         insert((HeapNode)newNextRecord);
      }
   }

   private static Address expand(HeapRecord record, int oldSize, int newSize) {
      HeapRecord nextRecord = HeapRecord.next(record);
      if (nextRecord.toAddress() == end) {
         if (!tryExtend(newSize - oldSize, record)) {
            return null;
         }
      } else if (!HeapRecord.isAllocated(nextRecord)) {
         HeapRecord nextNextRecord = HeapRecord.next(nextRecord);
         if (nextNextRecord.toAddress() == end && !tryExtend(newSize - oldSize, nextRecord)) {
            return null;
         }
      }

      if (!HeapRecord.isAllocated(nextRecord) && HeapRecord.size(nextRecord) + Structure.sizeOf(HeapRecord.class) >= newSize - oldSize) {
         expandInPlace(record, oldSize, newSize, nextRecord);
         return HeapRecord.dataOf(record);
      } else {
         Address newRegion = alloc(newSize);
         Address oldAddress = HeapRecord.dataOf(record);
         Address.moveMemoryBlock(oldAddress, newRegion, oldSize);
         release(oldAddress);
         return newRegion;
      }
   }

   private static void expandInPlace(HeapRecord record, int oldSize, int newSize, HeapRecord nextRecord) {
      int oldNextRecordSize = HeapRecord.size(nextRecord);
      HeapRecord nextNextRecord = HeapRecord.next(nextRecord);
      HeapRecord newNextRecord = HeapRecord.next(record, newSize);
      if (nextNextRecord.toAddress().isLessThan(newNextRecord.toAddress().add(Structure.sizeOf(HeapNode.class)))) {
         newSize = (int)nextNextRecord.toAddress().diff(HeapRecord.dataOf(record));
         newNextRecord = nextNextRecord;
      }

      delete((HeapNode)nextRecord);
      record.size = newSize | 1;
      if (nextNextRecord.toAddress() == newNextRecord.toAddress()) {
         if (newNextRecord.toAddress().isLessThan(end)) {
            newNextRecord.previousSize = newSize;
         }
      } else {
         newNextRecord.previousSize = newSize;
         newNextRecord.size = oldNextRecordSize - (newSize - oldSize);
         if (nextNextRecord.toAddress().isLessThan(end)) {
            nextNextRecord.previousSize = newNextRecord.size;
         }

         insert((HeapNode)newNextRecord);
      }
   }

   private static boolean tryExtend(int bytes, HeapRecord last) {
      if (currentSize + bytes > maxSize) {
         return false;
      }

      int grownBytes = grow(bytes);
      if (grownBytes == 0) {
         return false;
      }

      currentSize += grownBytes;
      if (!HeapRecord.isAllocated(last)) {
         delete((HeapNode)last);
         last.size += grownBytes;
         insert((HeapNode)last);
      } else {
         HeapNode newEmpty = (HeapNode)end.toStructure();
         newEmpty.size = grownBytes - Structure.sizeOf(HeapRecord.class);
         newEmpty.previousSize = HeapRecord.size(last);
         insert(newEmpty);
      }

      end = start.add(currentSize);
      maxSize = Math.max(currentSize, maxSize);
      notifyHeapResized();
      return grownBytes >= bytes;
   }

   @Import(module = "teavmMemory", name = "notifyHeapResized")
   private static native void notifyHeapResized();

   private static HeapNode lastRecord() {
      HeapRecord record = (HeapRecord)start.toStructure();
      HeapRecord result = null;

      while (record.toAddress() != end) {
         result = record;
         record = HeapRecord.next(record);
      }

      return (HeapNode)result;
   }

   private static Address tryAlloc(int bytes) {
      if (root == null) {
         return Address.fromInt(0);
      }

      HeapNode free = findFree(root, bytes);
      return free == null ? Address.fromInt(0) : allocFromFreeRecord(free, bytes);
   }

   private static Address allocFromFreeRecord(HeapNode free, int bytes) {
      delete(free);
      int currentSize = HeapNode.size(free);
      if (currentSize - bytes >= Structure.sizeOf(HeapNode.class) + Structure.sizeOf(HeapRecord.class)) {
         HeapNode nextFree = (HeapNode)HeapRecord.next(free, bytes);
         nextFree.size = currentSize - bytes - Structure.sizeOf(HeapRecord.class);
         nextFree.previousSize = bytes;
         insert(nextFree);
         free.size = bytes | 1;
         HeapNode nextNext = (HeapNode)HeapRecord.next(nextFree);
         if (nextNext.toAddress().isLessThan(end)) {
            nextNext.previousSize = nextNext.previousSize - (bytes + Structure.sizeOf(HeapRecord.class));
         }
      } else {
         free.size |= 1;
      }

      return free.toAddress().add(Structure.sizeOf(HeapRecord.class));
   }

   @Export(name = "teavm.free")
   public static void release(Address address) {
      HeapRecord record = HeapRecord.recordOf(address);
      int size = HeapRecord.size(record);
      if (start.isLessThan(record.toAddress())) {
         HeapRecord previousRecord = HeapRecord.previous(record);
         if (!HeapRecord.isAllocated(previousRecord)) {
            size += previousRecord.size + Structure.sizeOf(HeapRecord.class);
            delete((HeapNode)previousRecord);
            record = previousRecord;
         }
      }

      HeapRecord nextRecord = HeapRecord.next(record, size);
      if (nextRecord.toAddress().isLessThan(end) && !HeapRecord.isAllocated(nextRecord)) {
         int bytes = nextRecord.size + Structure.sizeOf(HeapRecord.class);
         size += bytes;
         delete((HeapNode)nextRecord);
      }

      record.size = size;
      insert((HeapNode)record);
      HeapRecord next = HeapRecord.next(record);
      if (next.toAddress().isLessThan(end)) {
         next.previousSize = size;
      }
   }

   static void insert(HeapNode node) {
      node.height = 1;
      node.list = null;
      node.left = null;
      node.right = null;
      node.flags = 0;
      root = insert(root, node);
   }

   private static HeapNode insert(HeapNode into, HeapNode node) {
      if (into == null) {
         node.left = null;
         node.right = null;
         node.flags = (short)(node.flags & -2);
         return node;
      }

      if (node.size < into.size) {
         into.left = insert(into.left, node);
      } else {
         if (node.size <= into.size) {
            node.left = into;
            node.flags = (short)(node.flags | 1);
            node.right = into.list;
            if (into.list != null) {
               into.list.left = node;
            }

            into.list = node;
            return into;
         }

         into.right = insert(into.right, node);
      }

      HeapNode.fix(into);
      return HeapNode.balance(into);
   }

   static void delete(HeapNode node) {
      if ((node.flags & 1) == 0) {
         if (node.list != null) {
            HeapNode newHead = node.list;
            updateListHolder(node);
            newHead.list = newHead.right;
            newHead.left = node.left;
            newHead.right = node.right;
            newHead.height = node.height;
            newHead.flags = (short)(newHead.flags & -2);
         } else {
            root = delete(root, node);
         }
      } else {
         if ((node.left.flags & 1) == 0) {
            node.left.list = node.right;
         } else {
            node.left.right = node.right;
         }

         if (node.right != null) {
            node.right.left = node.left;
         }
      }
   }

   private static void updateListHolder(HeapNode node) {
      if (root == node) {
         root = node.list;
      } else {
         HeapNode at = root;

         while (true) {
            if (at.left == node) {
               at.left = node.list;
               break;
            }

            if (at.right == node) {
               at.right = node.list;
               break;
            }

            if (node.size < at.size) {
               at = at.left;
            } else {
               at = at.right;
            }
         }
      }
   }

   private static HeapNode delete(HeapNode root, HeapNode node) {
      if (root == null) {
         return null;
      }

      if (node.size < root.size) {
         root.left = delete(root.left, node);
      } else if (node.size > root.size) {
         root.right = delete(root.right, node);
      } else {
         if (root.left == null) {
            return root.right;
         }

         if (root.right == null) {
            return root.left;
         }

         HeapNode left = root.left;
         HeapNode min = findMinimum(root.right);
         min.right = removeMinimum(root.right);
         min.left = left;
         root = min;
      }

      HeapNode.fix(root);
      return HeapNode.balance(root);
   }

   private static HeapNode removeMinimum(HeapNode node) {
      if (node.left == null) {
         return node.right;
      }

      node.left = removeMinimum(node.left);
      HeapNode.fix(node);
      return HeapNode.balance(node);
   }

   private static HeapNode findMinimum(HeapNode node) {
      while (node.left != null) {
         node = node.left;
      }

      return node;
   }

   private static HeapNode findFree(HeapNode node, int bytes) {
      while (node != null) {
         if (node.size < bytes) {
            node = node.right;
         } else {
            if (node.size > bytes && node.left != null && node.left.size >= bytes) {
               node = node.left;
               continue;
            }
            break;
         }
      }

      return node;
   }

   @Intrinsified
   static native int grow(int var0);
}
