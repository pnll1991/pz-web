package org.teavm.runtime.heap;

import org.teavm.interop.Address;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
class HeapRecord extends Structure {
   static final int ALLOCATED = 1;
   int size;
   int previousSize;

   static int size(HeapRecord record) {
      return record.size & -2;
   }

   static boolean isAllocated(HeapRecord record) {
      return (record.size & 1) != 0;
   }

   static HeapRecord next(HeapRecord record) {
      return (HeapRecord)dataOf(record).add(size(record)).toStructure();
   }

   static HeapRecord next(HeapRecord record, int size) {
      return (HeapRecord)dataOf(record).add(size).toStructure();
   }

   static HeapRecord previous(HeapRecord record) {
      return recordOf(record.toAddress().add(-record.previousSize));
   }

   static Address dataOf(HeapRecord record) {
      return record.toAddress().add(Structure.sizeOf(HeapRecord.class));
   }

   static HeapRecord recordOf(Address address) {
      return (HeapRecord)address.add(-Structure.sizeOf(HeapRecord.class)).toStructure();
   }
}
