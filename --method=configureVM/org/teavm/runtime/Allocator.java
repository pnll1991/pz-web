package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;

@StaticInit
@Unmanaged
public final class Allocator {
   private Allocator() {
   }

   public static Address allocate(RuntimeClass tag) {
      RuntimeObject object = GC.alloc(tag.size);
      Address.fillZero(object.toAddress(), tag.size);
      object.classReference = tag.pack();
      return object.toAddress();
   }

   public static Address allocateArray(RuntimeClass tag, int size) {
      int itemSize = RuntimeClass.isPrimitive(tag.itemType) ? tag.itemType.size : Address.sizeOf();
      int sizeInBytes = Address.align(Address.fromInt(Structure.sizeOf(RuntimeArray.class)), itemSize).toInt();
      sizeInBytes += itemSize * size;
      sizeInBytes = Address.align(Address.fromInt(sizeInBytes), Address.sizeOf()).toInt();
      Address result = GC.alloc(sizeInBytes).toAddress();
      Address.fillZero(result, sizeInBytes);
      RuntimeArray array = (RuntimeArray)result.toStructure();
      array.classReference = tag.pack();
      array.size = size;
      return result;
   }

   public static RuntimeArray allocateMultiArray(RuntimeClass tag, Address dimensions, int dimensionCount) {
      int size = dimensions.getInt();
      RuntimeArray array = (RuntimeArray)allocateArray(tag, dimensions.getInt()).toStructure();
      if (dimensionCount > 1) {
         Address arrayData = ((RuntimeArray)Structure.add(RuntimeArray.class, array, 1)).toAddress();
         arrayData = Address.align(arrayData, Address.sizeOf());

         for (int i = 0; i < size; i++) {
            RuntimeArray innerArray = allocateMultiArray(tag.itemType, dimensions.add(4), dimensionCount - 1);
            arrayData.putAddress(innerArray.toAddress());
            arrayData = arrayData.add(Address.sizeOf());
         }
      }

      return array;
   }
}
