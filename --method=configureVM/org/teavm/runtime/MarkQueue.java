package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
final class MarkQueue {
   private static int head;
   private static int tail;
   private static int limit;

   private MarkQueue() {
   }

   static void init() {
      head = 0;
      tail = 0;
      limit = GC.gcStorageSize() / 4;
   }

   static void enqueue(RuntimeObject object) {
      GC.gcStorageAddress().add(4 * tail).putInt(pack(object.toAddress()));
      if (++tail >= limit) {
         tail = 0;
      }

      if (tail == head) {
         ExceptionHandling.printStack();
         GC.outOfMemory();
      }
   }

   static RuntimeObject dequeue() {
      Address result = unpack(GC.gcStorageAddress().add(4 * head).getInt());
      if (++head >= limit) {
         head = 0;
      }

      return (RuntimeObject)result.toStructure();
   }

   private static int pack(Address address) {
      return (int)(address.toLong() - GC.heapAddress().toLong() >>> 2);
   }

   private static Address unpack(int packed) {
      return GC.heapAddress().add((long)packed << 2);
   }

   static boolean isEmpty() {
      return head == tail;
   }
}
