package org.teavm.runtime;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Address;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
public final class GC {
   private static final byte CARD_VALID = 1;
   private static final byte CARD_YOUNG_GEN = 2;
   private static final byte CARD_GAP = 4;
   private static final byte CARD_RELOCATABLE = 8;
   private static final int MIN_CHUNK_SIZE = 8;
   static Address currentChunkLimit = GC.currentChunk.toAddress().add(GC.currentChunk.size);
   static FreeChunk currentChunk = (FreeChunk)heapAddress().toStructure();
   static FreeChunkHolder currentChunkPointer = (FreeChunkHolder)gcStorageAddress().toStructure();
   static int freeChunks = 1;
   static int totalChunks = 1;
   static int freeMemory = (int)availableBytes();
   static RuntimeReference firstWeakReference;
   static FreeChunk lastChunk;
   static RelocationBlock lastRelocationBlock;
   static boolean isFullGC = true;
   private static int youngGCCount;
   private static RuntimeBuffer firstDirectBuffer = null;

   private GC() {
   }

   static native Address gcStorageAddress();

   static native int gcStorageSize();

   public static native Address heapAddress();

   private static native GC.Region regionsAddress();

   private static native Address cardTable();

   private static native int regionMaxCount();

   public static native long availableBytes();

   public static native long minAvailableBytes();

   public static native long maxAvailableBytes();

   public static native void resizeHeap(long var0);

   public static native boolean canShrinkHeap();

   private static native int regionSize();

   public static native void writeBarrier(RuntimeObject var0);

   @Import(name = "teavm_outOfMemory")
   public static native void outOfMemory();

   public static int getFreeMemory() {
      return freeMemory;
   }

   private static int getRegionCount() {
      return (int)(availableBytes() / regionSize()) + 1;
   }

   public static void registerDirectBuffer(RuntimeBuffer buffer) {
      buffer.nextRef = firstDirectBuffer;
      firstDirectBuffer = buffer;
   }

   public static RuntimeObject alloc(int size) {
      FreeChunk current = currentChunk;
      Address next = current.toAddress().add(size);
      if (!next.add(Structure.sizeOf(FreeChunk.class)).isLessThan(currentChunkLimit)) {
         getNextChunk(size);
         current = currentChunk;
         next = current.toAddress().add(size);
      }

      currentChunk = (FreeChunk)next.toStructure();
      freeMemory -= size;
      MemoryTrace.allocate(current.toAddress(), size);
      return (RuntimeObject)current.toAddress().toStructure();
   }

   private static void getNextChunk(int size) {
      if (!getNextChunkIfPossible(size)) {
         collectGarbageImpl(size);
         if (!hasAvailableMemory(size)) {
            collectGarbageFullImpl(size);
            if (!hasAvailableMemory(size)) {
               ExceptionHandling.printStack();
               outOfMemory();
            }
         }
      }
   }

   private static boolean hasAvailableMemory(int size) {
      return currentChunk.size == size || currentChunk.size > size + 8 || getNextChunkIfPossible(size);
   }

   private static boolean getNextChunkIfPossible(int size) {
      while (true) {
         if (currentChunk.toAddress().isLessThan(currentChunkLimit)) {
            currentChunk.classReference = 0;
            currentChunk.size = (int)(currentChunkLimit.toLong() - currentChunk.toAddress().toLong());
         }

         if (--freeChunks == 0) {
            return false;
         }

         currentChunkPointer = (FreeChunkHolder)Structure.add(FreeChunkHolder.class, currentChunkPointer, 1);
         currentChunk = currentChunkPointer.value;
         if (currentChunk.size >= size + 8 || currentChunk.size == size) {
            currentChunkLimit = currentChunk.toAddress().add(currentChunk.size);
            return true;
         }

         freeMemory = freeMemory - currentChunk.size;
      }
   }

   @Export(name = "teavm_gc_collect")
   public static void collectGarbage() {
      fixHeap();
      collectGarbageImpl(0);
   }

   @Export(name = "teavm_gc_collectFull")
   public static void collectGarbageFull() {
      fixHeap();
      collectGarbageFullImpl(0);
   }

   private static void collectGarbageFullImpl(int size) {
      triggerFullGC();
      collectGarbageImpl(size);
   }

   private static void triggerFullGC() {
      isFullGC = true;
      int regionsCount = getRegionCount();
      Address.fill(cardTable(), (byte)0, getRegionCount());
      Address.fill(regionsAddress().toAddress(), (byte)0, regionsCount * Structure.sizeOf(GC.Region.class));
   }

   private static void collectGarbageImpl(int size) {
      doCollectGarbage();
      long minRequestedSize = 0L;
      if (!hasAvailableChunk(size)) {
         minRequestedSize = computeMinRequestedSize(size);
      }

      if (!isFullGC) {
         int youngGCLimit = canShrinkHeap() ? 2 : 8;
         if (++youngGCCount >= youngGCLimit && isAboutToExpand(minRequestedSize)) {
            triggerFullGC();
            doCollectGarbage();
            youngGCCount = 0;
         }
      } else {
         youngGCCount = 0;
      }

      isFullGC = false;
      resizeHeapIfNecessary(minRequestedSize);
      currentChunk = currentChunkPointer.value;
      currentChunkLimit = currentChunk.toAddress().add(currentChunk.size);
      Address.fill(cardTable(), (byte)1, getRegionCount());
   }

   private static void doCollectGarbage() {
      MemoryTrace.gcStarted(isFullGC);
      if (!isFullGC) {
         storeGapsInCardTable();
      }

      mark();
      processReferences();
      processDirectBuffers();
      processStringPool();
      sweep();
      defragment();
      updateFreeMemory();
      MemoryTrace.gcCompleted();
      totalChunks = freeChunks;
   }

   private static boolean hasAvailableChunk(int size) {
      if (size == 0) {
         return true;
      }

      FreeChunkHolder ptr = currentChunkPointer;

      for (int i = 0; i < freeChunks; i++) {
         if (size == ptr.value.size || size + 8 <= ptr.value.size) {
            return true;
         }

         ptr = (FreeChunkHolder)Structure.add(FreeChunkHolder.class, ptr, 1);
      }

      return false;
   }

   private static long computeMinRequestedSize(int size) {
      if (lastChunk.classReference == 0) {
         size -= lastChunk.size;
      }

      return availableBytes() + size;
   }

   @Export(name = "teavm_gc_fixHeap")
   public static void fixHeap() {
      if (freeChunks > 0) {
         currentChunk.classReference = 0;
         currentChunk.size = (int)(currentChunkLimit.toLong() - currentChunk.toAddress().toLong());
      }
   }

   @Export(name = "teavm_gc_tryShrink")
   public static void tryShrink() {
      long availableBytes = availableBytes();
      long occupiedMemory = availableBytes - freeMemory;
      if (occupiedMemory < availableBytes / 4L) {
         collectGarbageFull();
      }
   }

   private static void mark() {
      MemoryTrace.markStarted();
      firstWeakReference = null;
      markFromStaticFields();
      markFromClasses();
      markFromStack();
      if (!isFullGC) {
         markFromOldGeneration();
      }

      MemoryTrace.markCompleted();
   }

   private static void markFromStaticFields() {
      Address staticRoots = Mutator.getStaticGCRoots();
      int staticCount = staticRoots.getInt();

      for (Address var3 = staticRoots.add(Address.sizeOf()); staticCount-- > 0; var3 = var3.add(Address.sizeOf())) {
         RuntimeObject object = (RuntimeObject)var3.getAddress().getAddress().toStructure();
         if (object != null) {
            mark(object);
         }
      }
   }

   private static void markFromClasses() {
      for (RuntimeClass cls = RuntimeClass.first(); cls != null; cls = cls.next) {
         markFromClass(cls);
      }
   }

   private static void markFromClass(RuntimeClass cls) {
      if (cls.classObject != null) {
         mark(cls.classObject);
      }
   }

   private static void markFromStack() {
      for (Address stackRoots = ShadowStack.getStackTop(); stackRoots != null; stackRoots = ShadowStack.getNextStackFrame(stackRoots)) {
         int count = ShadowStack.getStackRootCount(stackRoots);

         for (Address stackRootsPtr = ShadowStack.getStackRootPointer(stackRoots); count-- > 0; stackRootsPtr = stackRootsPtr.add(Address.sizeOf())) {
            RuntimeObject obj = (RuntimeObject)stackRootsPtr.getAddress().toStructure();
            mark(obj);
         }
      }
   }

   private static void markFromOldGeneration() {
      int validMask = 16843009;
      int regionsCount = getRegionCount();
      int regionSize = regionSize();
      Address cardPtr = cardTable();
      Address regionPtr = heapAddress();

      int regionIndex;
      for (regionIndex = 0; regionIndex < regionsCount - 3; regionIndex += 4) {
         int n = cardPtr.getInt();
         if ((n & validMask) != validMask) {
            for (int i = 0; i < 4; i++) {
               int var8 = cardPtr.add(i).getByte();
               if ((var8 & 1) == 0) {
                  markFromRegion(regionIndex + i);
               }
            }
         }

         cardPtr = cardPtr.add(4);
         regionPtr = regionPtr.add(4 * regionSize);
      }

      while (regionIndex < regionsCount) {
         if ((cardPtr.getByte() & 1) == 0) {
            markFromRegion(regionIndex);
         }

         cardPtr = cardPtr.add(1);
         regionIndex++;
      }
   }

   private static void markFromRegion(int regionIndex) {
      Address card = cardTable().add(regionIndex);
      int regionOffset = ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), regionIndex)).start;
      if (regionOffset == 0) {
         card.putByte((byte)(card.getByte() | 1));
      } else {
         regionOffset--;
         int regionSize = regionSize();
         Address regionStart = heapAddress().add(regionIndex * regionSize);
         MemoryTrace.reportDirtyRegion(regionStart);
         Address regionEnd = regionStart.add(regionSize);
         FreeChunk object = (FreeChunk)regionStart.add(regionOffset).toStructure();
         Address heapLimit = heapAddress().add(availableBytes());
         if (heapLimit.isLessThan(regionEnd)) {
            regionEnd = heapLimit;
         }

         boolean objectMarked = false;

         while (object.toAddress().isLessThan(regionEnd)) {
            int header = object.classReference;
            if (header != 0 && (header & 1073741824) != 0) {
               objectMarked |= doMarkOldGeneration((RuntimeObject)object.toAddress().toStructure());
            }

            object = (FreeChunk)object.toAddress().add(objectSize(object)).toStructure();
         }

         if (!objectMarked) {
            card.putByte((byte)(card.getByte() | 1));
         }
      }
   }

   private static void mark(RuntimeObject object) {
      if (object != null && !isMarked(object)) {
         MarkQueue.init();
         enqueueMark(object);
         doProcessMarkQueue();
      }
   }

   private static boolean doMarkOldGeneration(RuntimeObject object) {
      MarkQueue.init();
      boolean hasObjectsFromYoungGen = markObjectData(object);
      doProcessMarkQueue();
      return hasObjectsFromYoungGen;
   }

   private static void doProcessMarkQueue() {
      while (!MarkQueue.isEmpty()) {
         RuntimeObject object = MarkQueue.dequeue();
         MemoryTrace.mark(object.toAddress());
         long offset = object.toAddress().toLong() - heapAddress().toLong();
         GC.Region region = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), (int)(offset / regionSize()));
         short relativeOffset = (short)(offset % regionSize() + 1L);
         if (region.start == 0 || region.start > relativeOffset) {
            region.start = relativeOffset;
         }

         Address cardTableItem = cardTable().add(offset / regionSize());
         cardTableItem.putByte((byte)(cardTableItem.getByte() | 2));
         markObjectData(object);
      }
   }

   private static boolean markObjectData(RuntimeObject object) {
      RuntimeClass cls = RuntimeClass.getClass(object);
      return cls.itemType == null ? markObject(cls, object) : markArray(cls, (RuntimeArray)object);
   }

   private static boolean markObject(RuntimeClass cls, RuntimeObject object) {
      boolean hasObjectsFromYoungGen = false;

      while (cls != null) {
         int type = cls.flags >> 1 & 7;
         switch (type) {
            case 1:
               hasObjectsFromYoungGen |= markWeakReference((RuntimeReference)object);
               break;
            case 2:
               hasObjectsFromYoungGen |= markReferenceQueue((RuntimeReferenceQueue)object);
            case 3:
               break;
            default:
               hasObjectsFromYoungGen |= markFields(cls, object);
         }

         cls = cls.superclass;
      }

      return hasObjectsFromYoungGen;
   }

   private static boolean markWeakReference(RuntimeReference object) {
      boolean hasObjectsFromYoungGen = false;
      if (object.queue != null) {
         hasObjectsFromYoungGen |= enqueueMark(object.queue);
         if (object.next != null && object.object != null) {
            hasObjectsFromYoungGen |= enqueueMark(object.object);
         }
      }

      if (object.next != null) {
         hasObjectsFromYoungGen |= enqueueMark(object.next);
      } else if (object.object != null) {
         object.next = firstWeakReference;
         firstWeakReference = object;
      }

      return hasObjectsFromYoungGen;
   }

   private static boolean markReferenceQueue(RuntimeReferenceQueue object) {
      RuntimeReference reference = object.first;
      boolean hasObjectsFromYoungGen = false;
      if (reference != null) {
         hasObjectsFromYoungGen |= enqueueMark(reference);
      }

      return hasObjectsFromYoungGen;
   }

   private static boolean markFields(RuntimeClass cls, RuntimeObject object) {
      Address layout = cls.layout;
      if (layout == null) {
         return false;
      }

      boolean hasObjectsFromYoungGen = false;
      short fieldCount = layout.getShort();

      while (fieldCount-- > 0) {
         layout = layout.add(2);
         int fieldOffset = layout.getShort();
         RuntimeObject reference = (RuntimeObject)object.toAddress().add(fieldOffset).getAddress().toStructure();
         hasObjectsFromYoungGen |= enqueueMark(reference);
      }

      return hasObjectsFromYoungGen;
   }

   private static boolean markArray(RuntimeClass cls, RuntimeArray array) {
      if (RuntimeClass.isPrimitive(cls.itemType)) {
         return false;
      }

      Address base = Address.align(array.toAddress().add(RuntimeArray.class, 1), Address.sizeOf());
      boolean hasObjectsFromYoungGen = false;

      for (int i = 0; i < array.size; i++) {
         RuntimeObject reference = (RuntimeObject)base.getAddress().toStructure();
         hasObjectsFromYoungGen |= enqueueMark(reference);
         base = base.add(Address.sizeOf());
      }

      return hasObjectsFromYoungGen;
   }

   private static boolean enqueueMark(RuntimeObject object) {
      if (object == null) {
         return false;
      } else if (!isMarked(object)) {
         doEnqueueMark(object);
         return true;
      } else {
         return (object.classReference & 1073741824) == 0;
      }
   }

   private static void doEnqueueMark(RuntimeObject object) {
      if (isFullGC) {
         object.classReference |= -1073741824;
      } else {
         object.classReference |= Integer.MIN_VALUE;
      }

      MarkQueue.enqueue(object);
   }

   private static void processReferences() {
      RuntimeReference reference = firstWeakReference;

      while (reference != null) {
         RuntimeReference next = reference.next;
         reference.next = null;
         if (!isMarked(reference.object)) {
            reference.object = null;
            RuntimeReferenceQueue queue = reference.queue;
            if (queue != null) {
               if (queue.first == null) {
                  queue.first = reference;
               } else {
                  queue.last.next = reference;
                  makeInvalid(queue.last);
               }

               queue.last = reference;
               makeInvalid(queue);
            }
         }

         reference = next;
      }
   }

   private static void makeInvalid(RuntimeObject object) {
      long offset = object.toAddress().toLong() - heapAddress().toLong();
      Address cardTableItem = cardTable().add(offset / regionSize());
      cardTableItem.putByte((byte)(cardTableItem.getByte() & -2));
   }

   private static void processDirectBuffers() {
      RuntimeBuffer buffer = firstDirectBuffer;
      RuntimeBuffer prevBuffer = null;

      while (buffer != null) {
         RuntimeBuffer next = buffer.nextRef;
         if ((buffer.classReference & -2147483648) == 0) {
            freeBufferContent(buffer);
         } else {
            if (prevBuffer == null) {
               firstDirectBuffer = buffer;
            } else {
               prevBuffer.nextRef = buffer;
            }

            prevBuffer = buffer;
         }

         buffer = next;
      }

      if (prevBuffer == null) {
         firstDirectBuffer = null;
      } else {
         prevBuffer.nextRef = null;
      }
   }

   private static void freeBufferContent(RuntimeBuffer buffer) {
      for (RuntimeClass cls = RuntimeClass.getClass(buffer); cls != null; cls = cls.superclass) {
         if (cls.enumValues != null) {
            int fieldOffset = cls.enumValues.toInt();
            Address fieldAddress = buffer.toAddress().add(fieldOffset).add(Address.sizeOf());
            Address dataAddress = fieldAddress.getAddress();
            if (dataAddress == null) {
               free(dataAddress);
            }

            return;
         }
      }
   }

   private static void processStringPool() {
      Mutator.stringHashtableRewind();

      while (true) {
         RuntimeObject current = Mutator.stringHashtableCurrent();
         if (current == null) {
            return;
         }

         if (!isMarked(current)) {
            Mutator.stringHashtableDelete();
         } else {
            Mutator.stringHashtableNext();
         }
      }
   }

   private static void sweep() {
      MemoryTrace.sweepStarted();
      currentChunkPointer = (FreeChunkHolder)gcStorageAddress().toStructure();
      freeChunks = 0;
      totalChunks = 0;
      FreeChunk object = (FreeChunk)heapAddress().toStructure();
      FreeChunk lastFreeSpace = null;
      long heapSize = availableBytes();
      int regionsCount = getRegionCount();
      Address currentRegionEnd = null;
      Address limit = heapAddress().add(heapSize);

      label91:
      while (object.toAddress().isLessThan(limit)) {
         int tag = object.classReference;
         boolean free;
         if (tag == 0) {
            free = true;
         } else {
            free = (tag & -2147483648) == 0;
            if (free && !isFullGC && (tag & 1073741824) != 0) {
               free = false;
            }

            if (!free) {
               tag &= Integer.MAX_VALUE;
            }

            object.classReference = tag;
         }

         if (free) {
            if (lastFreeSpace == null) {
               lastFreeSpace = object;
            }

            if (!object.toAddress().isLessThan(currentRegionEnd)) {
               int currentRegionIndex = (int)((object.toAddress().toLong() - heapAddress().toLong()) / regionSize());
               GC.Region currentRegion = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), currentRegionIndex);
               currentRegionEnd = heapAddress().add((currentRegionIndex + 1) * regionSize());
               if (currentRegion.start == 0) {
                  while (++currentRegionIndex != regionsCount) {
                     currentRegion = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), currentRegionIndex);
                     if (currentRegion.start != 0) {
                        Address newRegionStart = heapAddress().add(currentRegionIndex * regionSize());
                        object = (FreeChunk)newRegionStart.add(currentRegion.start - 1).toStructure();
                        currentRegionEnd = newRegionStart.add(regionSize());
                        continue label91;
                     }
                  }

                  object = (FreeChunk)limit.toStructure();
                  break;
               }
            }
         } else {
            if (lastFreeSpace != null) {
               freeMemory(lastFreeSpace, object);
               lastFreeSpace = null;
            }

            if (!object.toAddress().isLessThan(currentRegionEnd)) {
               int currentRegionIndex = (int)((object.toAddress().toLong() - heapAddress().toLong()) / regionSize());
               currentRegionEnd = heapAddress().add((currentRegionIndex + 1) * regionSize());
               FreeChunk formerObject = object;
               if (!isFullGC && (cardTable().add(currentRegionIndex).getByte() & 2) == 0 && (cardTable().add(currentRegionIndex).getByte() & 4) == 0) {
                  while (++currentRegionIndex != regionsCount) {
                     GC.Region currentRegion = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), currentRegionIndex);
                     if (currentRegion.start == 0
                        || (cardTable().add(currentRegionIndex).getByte() & 2) != 0
                        || (cardTable().add(currentRegionIndex).getByte() & 4) != 0) {
                        break;
                     }
                  }

                  GC.Region currentRegion = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), --currentRegionIndex);
                  Address newRegionStart = heapAddress().add(currentRegionIndex * regionSize());
                  object = (FreeChunk)newRegionStart.add(currentRegion.start - 1).toStructure();
                  currentRegionEnd = newRegionStart.add(regionSize());
                  if (formerObject.toAddress().isLessThan(object.toAddress())) {
                     continue;
                  }
               }
            }
         }

         int size = objectSize(object);
         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }

      if (lastFreeSpace != null) {
         freeMemory(lastFreeSpace, object);
      }

      currentChunkPointer = (FreeChunkHolder)gcStorageAddress().toStructure();
      MemoryTrace.sweepCompleted();
   }

   private static void storeGapsInCardTable() {
      for (int i = 0; i < totalChunks; i++) {
         FreeChunk freeChunkStart = ((FreeChunkHolder)FreeChunkHolder.add(FreeChunkHolder.class, (FreeChunkHolder)gcStorageAddress().toStructure(), i)).value;
         long freeChunkOffset = freeChunkStart.toAddress().toLong() - heapAddress().toLong();
         long freeChunkEndOffset = freeChunkOffset + freeChunkStart.size;
         int startRegion = (int)(freeChunkOffset / regionSize());
         int endRegion = (int)(freeChunkEndOffset / regionSize());

         for (int region = startRegion; region <= endRegion; region++) {
            Address card = cardTable().add(region);
            card.putByte((byte)(card.getByte() | 4));
         }
      }
   }

   private static void clearGapsFromCardTable() {
      int gapMask = -67372037;
      int regionsCount = getRegionCount();
      Address cardPtr = cardTable();

      int regionIndex;
      for (regionIndex = 0; regionIndex < regionsCount - 3; regionIndex += 4) {
         cardPtr.putInt(cardPtr.getInt() & gapMask);
         cardPtr = cardPtr.add(4);
      }

      while (regionIndex < regionsCount) {
         cardPtr.putByte((byte)(cardPtr.getByte() & -5));
         cardPtr = cardPtr.add(1);
         regionIndex++;
      }
   }

   private static void freeMemory(FreeChunk from, FreeChunk to) {
      from.classReference = 0;
      from.size = (int)(to.toAddress().toLong() - from.toAddress().toLong());
      MemoryTrace.free(from.toAddress(), from.size);
      currentChunkPointer.value = from;
      currentChunkPointer = (FreeChunkHolder)Structure.add(FreeChunkHolder.class, currentChunkPointer, 1);
      freeChunks++;
      totalChunks++;
   }

   private static void defragment() {
      MemoryTrace.defragStarted();
      clearGapsFromCardTable();
      storeGapsInCardTable();
      markStackRoots();
      moveNonRelocatableObjectsToOldGeneration();
      calculateRelocationTargets();
      updatePointersFromStaticRoots();
      updatePointersFromClasses();
      updatePointersFromObjects();
      updatePointersFromStringPool();
      restoreObjectHeaders();
      relocateObjects();
      putNewFreeChunks();
      MemoryTrace.defragCompleted();
   }

   private static void markStackRoots() {
      Address relocationThreshold = currentChunkPointer.value.toAddress();

      for (Address stackRoots = ShadowStack.getStackTop(); stackRoots != null; stackRoots = ShadowStack.getNextStackFrame(stackRoots)) {
         int count = ShadowStack.getStackRootCount(stackRoots);

         for (Address stackRootsPtr = ShadowStack.getStackRootPointer(stackRoots); count-- > 0; stackRootsPtr = stackRootsPtr.add(Address.sizeOf())) {
            RuntimeObject obj = (RuntimeObject)stackRootsPtr.getAddress().toStructure();
            if (!obj.toAddress().isLessThan(relocationThreshold) && (isFullGC || (obj.classReference & 1073741824) == 0)) {
               obj.classReference |= Integer.MIN_VALUE;
            }
         }
      }
   }

   private static void moveNonRelocatableObjectsToOldGeneration() {
      Address limitAddress = currentChunkPointer.value.toAddress();
      long limit = limitAddress.toLong() - heapAddress().toLong();

      for (int region = 0; (long)region * regionSize() < limit; region++) {
         if ((cardTable().add(region).getByte() & 2) != 0) {
            moveObjectsToOldGenerationInRegion(region, limitAddress);
         }
      }
   }

   private static void moveObjectsToOldGenerationInRegion(int region, Address limit) {
      int regionOffset = ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), region)).start - 1;
      int regionSize = regionSize();
      Address regionStart = heapAddress().add(region * regionSize);
      Address regionEnd = regionStart.add(regionSize);
      FreeChunk object = (FreeChunk)regionStart.add(regionOffset).toStructure();
      if (limit.isLessThan(regionEnd)) {
         regionEnd = limit;
      }

      while (object.toAddress().isLessThan(regionEnd)) {
         int classRef = object.classReference;
         if (classRef != 0 && (classRef & 1073741824) == 0) {
            classRef |= 1073741824;
            object.classReference = classRef;
         }

         int size = objectSize(object);
         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }
   }

   private static void calculateRelocationTargets() {
      Address start = heapAddress();
      long heapSize = availableBytes();
      Address limit = start.add(heapSize);
      FreeChunkHolder freeChunkPointer = currentChunkPointer;
      int freeChunks = GC.freeChunks;
      FreeChunk freeChunk = currentChunkPointer.value;
      FreeChunk object = (FreeChunk)freeChunk.toAddress().add(freeChunk.size).toStructure();
      RelocationBlock relocationBlock = (RelocationBlock)((FreeChunkHolder)Structure.add(FreeChunkHolder.class, currentChunkPointer, freeChunks))
         .toAddress()
         .toStructure();
      Address relocationTarget = freeChunk.toAddress();
      relocationBlock.start = relocationTarget;
      relocationBlock.end = limit;
      relocationBlock.count = 0;
      RelocationBlock lastRelocationBlock = relocationBlock;
      int countInCurrentRelocationBlock = 0;
      Address relocations = ((FreeChunk)Structure.add(FreeChunk.class, freeChunk, 1)).toAddress();
      Address relocationsLimit = freeChunk.toAddress().add(freeChunk.size);
      Address currentRegionEnd = null;
      lastChunk = (FreeChunk)heapAddress().toStructure();
      boolean lastWasLocked = false;

      label95:
      while (object.toAddress().isLessThan(limit)) {
         int size = objectSize(object);
         if (object.classReference != 0) {
            Address nextRelocationTarget = null;
            boolean shouldRelocateObject = shouldRelocateObject(object);
            object.classReference |= 1073741824;
            if (shouldRelocateObject) {
               while (true) {
                  nextRelocationTarget = relocationTarget.add(size);
                  if (nextRelocationTarget == relocationBlock.end || nextRelocationTarget.add(7).isLessThan(relocationBlock.end)) {
                     break;
                  }

                  RelocationBlock nextRelocationBlock = (RelocationBlock)Structure.add(RelocationBlock.class, relocationBlock, 1);
                  if (nextRelocationBlock.start == object.toAddress()) {
                     shouldRelocateObject = false;
                     break;
                  }

                  relocationBlock.count = countInCurrentRelocationBlock;
                  countInCurrentRelocationBlock = 0;
                  relocationBlock = nextRelocationBlock;
                  relocationTarget = relocationBlock.start;
               }
            }

            if (!shouldRelocateObject) {
               if (!lastWasLocked) {
                  lastRelocationBlock.end = object.toAddress();
                  lastRelocationBlock = (RelocationBlock)Structure.add(RelocationBlock.class, lastRelocationBlock, 1);
                  lastRelocationBlock.end = limit;
                  lastWasLocked = true;
               }

               if (!isFullGC && !object.toAddress().isLessThan(currentRegionEnd)) {
                  int region = (int)((object.toAddress().toLong() - heapAddress().toLong()) / regionSize());
                  currentRegionEnd = heapAddress().add((long)regionSize() * (region + 1));
                  byte card = cardTable().add(region).getByte();
                  if ((card & 2) == 0 && (card & 4) == 0) {
                     do {
                        card = cardTable().add(++region).getByte();
                     } while ((card & 2) == 0 && (card & 4) == 0 && ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), region)).start != 0);

                     currentRegionEnd = heapAddress().add((long)regionSize() * (--region + 1));
                     int offset = ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), region)).start - 1;
                     object = (FreeChunk)heapAddress().add((long)regionSize() * region).add(offset).toStructure();
                     size = objectSize(object);
                  }
               }

               lastRelocationBlock.start = object.toAddress().add(size);
               lastRelocationBlock.count = 0;
               object.classReference &= Integer.MAX_VALUE;
               lastChunk = object;
            } else {
               lastWasLocked = false;

               while (!relocations.add(Structure.sizeOf(Relocation.class)).isLessThan(relocationsLimit)) {
                  if (--freeChunks == 0) {
                     lastRelocationBlock.end = object.toAddress();
                     break label95;
                  }

                  freeChunkPointer = (FreeChunkHolder)Structure.add(FreeChunkHolder.class, freeChunkPointer, 1);
                  freeChunk = freeChunkPointer.value;
                  relocations = ((FreeChunk)Structure.add(FreeChunk.class, freeChunk, 1)).toAddress();
                  relocationsLimit = freeChunk.toAddress().add(freeChunk.size);
               }

               Relocation relocation = (Relocation)relocations.toStructure();
               relocation.classBackup = object.classReference;
               relocation.sizeBackup = object.size;
               relocation.newAddress = relocationTarget;
               countInCurrentRelocationBlock++;
               relocations = relocations.add(Structure.sizeOf(Relocation.class));
               long targetAddress = relocation.toAddress().toLong();
               object.classReference = (int)(targetAddress >>> 33) | -2147483648;
               object.size = (int)(targetAddress >> 1);
               relocationTarget = nextRelocationTarget;
               int region = (int)((object.toAddress().toLong() - heapAddress().toLong()) / regionSize());
               Address card = cardTable().add(region);
               card.putByte((byte)(card.getByte() | 8));
            }
         } else {
            lastWasLocked = false;
         }

         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }

      relocationBlock.count = countInCurrentRelocationBlock;

      while (object.toAddress().isLessThan(limit)) {
         int size = objectSize(object);
         if (object.classReference != 0) {
            int classRef = object.classReference;
            classRef &= Integer.MAX_VALUE;
            classRef |= 1073741824;
            object.classReference = classRef;
         } else {
            lastRelocationBlock = (RelocationBlock)Structure.add(RelocationBlock.class, lastRelocationBlock, 1);
            lastRelocationBlock.start = object.toAddress();
            lastRelocationBlock.count = 0;
            lastRelocationBlock.end = lastRelocationBlock.start.add(size);
         }

         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }

      GC.lastRelocationBlock = lastRelocationBlock;
   }

   private static boolean shouldRelocateObject(FreeChunk object) {
      return (object.classReference & -2147483648) == 0 && (isFullGC || (object.classReference & 1073741824) == 0);
   }

   private static void updatePointersFromStaticRoots() {
      Address staticRoots = Mutator.getStaticGCRoots();
      int staticCount = staticRoots.getInt();

      for (Address var3 = staticRoots.add(Address.sizeOf()); staticCount-- > 0; var3 = var3.add(Address.sizeOf())) {
         Address staticRoot = var3.getAddress();
         staticRoot.putAddress(updatePointer(staticRoot.getAddress()));
      }
   }

   private static void updatePointersFromClasses() {
      for (RuntimeClass cls = RuntimeClass.first(); cls != null; cls = cls.next) {
         if (cls.classObject != null) {
            cls.classObject = (RuntimeObject)updatePointer(cls.classObject.toAddress()).toStructure();
         }
      }
   }

   private static void updatePointersFromObjects() {
      if (isFullGC) {
         updatePointersFromObjectsFull();
      } else {
         updatePointersFromObjectsYoung();
      }

      updateReferenceToFirstBuffer();
   }

   private static void updateReferenceToFirstBuffer() {
      if (firstDirectBuffer != null) {
         firstDirectBuffer = (RuntimeBuffer)updatePointer(firstDirectBuffer.toAddress()).toStructure();
      }
   }

   private static void updatePointersFromObjectsFull() {
      Address start = heapAddress();
      long heapSize = availableBytes();
      Address limit = start.add(heapSize);
      FreeChunk object = (FreeChunk)heapAddress().toStructure();

      while (object.toAddress().isLessThan(limit)) {
         int classRef = object.classReference;
         int size;
         if (classRef != 0) {
            Relocation relocation = getRelocation(object.toAddress());
            if (relocation != null) {
               classRef = relocation.classBackup;
            }

            RuntimeClass cls = RuntimeClass.unpack(classRef);
            RuntimeObject realObject = (RuntimeObject)object.toAddress().toStructure();
            updatePointers(cls, realObject);
            size = objectSize(realObject, cls);
         } else {
            size = object.size;
         }

         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }
   }

   private static void updatePointersFromObjectsYoung() {
      int validMask = 16843009;
      int youngMask = 33686018;
      int regionsCount = getRegionCount();
      int regionSize = regionSize();
      Address cardPtr = cardTable();
      Address regionPtr = heapAddress();

      int regionIndex;
      for (regionIndex = 0; regionIndex < regionsCount - 3; regionIndex += 4) {
         int n = cardPtr.getInt();
         if ((n & validMask) != validMask || (n & youngMask) != 0) {
            for (int i = 0; i < 4; i++) {
               int var9 = cardPtr.add(i).getByte();
               if ((var9 & 1) == 0 || (var9 & 2) != 0) {
                  updatePointersFromRegion(regionIndex + i);
               }
            }
         }

         cardPtr = cardPtr.add(4);
         regionPtr = regionPtr.add(4 * regionSize);
      }

      while (regionIndex < regionsCount) {
         int n = cardPtr.getByte();
         if ((n & 1) == 0 || (n & 2) != 0) {
            updatePointersFromRegion(regionIndex);
         }

         cardPtr = cardPtr.add(1);
         regionIndex++;
      }
   }

   private static void updatePointersFromRegion(int regionIndex) {
      int regionOffset = ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), regionIndex)).start - 1;
      if (regionOffset >= 0) {
         int regionSize = regionSize();
         Address regionStart = heapAddress().add(regionIndex * regionSize);
         Address regionEnd = regionStart.add(regionSize);
         FreeChunk object = (FreeChunk)regionStart.add(regionOffset).toStructure();
         Address heapLimit = heapAddress().add(availableBytes());
         if (heapLimit.isLessThan(regionEnd)) {
            regionEnd = heapLimit;
         }

         while (object.toAddress().isLessThan(regionEnd)) {
            int classRef = object.classReference;
            int size;
            if (classRef != 0) {
               Relocation relocation = getRelocation(object.toAddress());
               if (relocation != null) {
                  classRef = relocation.classBackup;
               }

               RuntimeClass cls = RuntimeClass.unpack(classRef);
               updatePointers(cls, (RuntimeObject)object.toAddress().toStructure());
               size = objectSize((RuntimeObject)object.toAddress().toStructure(), cls);
            } else {
               size = object.size;
            }

            object = (FreeChunk)object.toAddress().add(size).toStructure();
         }
      }
   }

   private static void updatePointers(RuntimeClass cls, RuntimeObject object) {
      if (cls.itemType == null) {
         updatePointersInObject(cls, object);
      } else {
         updatePointersInArray(cls, (RuntimeArray)object);
      }
   }

   private static void updatePointersInObject(RuntimeClass cls, RuntimeObject object) {
      while (cls != null) {
         int type = cls.flags >> 1 & 7;
         switch (type) {
            case 1:
               updatePointersInWeakReference((RuntimeReference)object);
               break;
            case 2:
               updatePointersInReferenceQueue((RuntimeReferenceQueue)object);
               break;
            default:
               updatePointersInFields(cls, object);
         }

         cls = cls.superclass;
      }
   }

   private static void updatePointersInWeakReference(RuntimeReference object) {
      object.queue = (RuntimeReferenceQueue)updatePointer(object.queue.toAddress()).toStructure();
      object.next = (RuntimeReference)updatePointer(object.next.toAddress()).toStructure();
      object.object = (RuntimeObject)updatePointer(object.object.toAddress()).toStructure();
   }

   private static void updatePointersInReferenceQueue(RuntimeReferenceQueue object) {
      object.first = (RuntimeReference)updatePointer(object.first.toAddress()).toStructure();
      object.last = (RuntimeReference)updatePointer(object.last.toAddress()).toStructure();
   }

   private static void updatePointersInFields(RuntimeClass cls, RuntimeObject object) {
      Address layout = cls.layout;
      int bufferFieldOffset = -1;
      if (cls.enumValues != null && (cls.modifiers & 65536) == 0) {
         bufferFieldOffset = cls.enumValues.toInt();
      }

      if (layout != null) {
         short fieldCount = layout.getShort();

         while (fieldCount-- > 0) {
            layout = layout.add(2);
            int fieldOffset = layout.getShort();
            Address referenceHolder = object.toAddress().add(fieldOffset);
            Address oldPointer = referenceHolder.getAddress();
            Address newPointer = updatePointer(oldPointer);
            referenceHolder.putAddress(newPointer);
            if (fieldOffset == bufferFieldOffset && oldPointer != null) {
               long diff = newPointer.diff(oldPointer);
               referenceHolder = referenceHolder.add(Address.sizeOf());
               referenceHolder.putAddress(referenceHolder.getAddress().add(diff));
            }
         }
      }

      if (bufferFieldOffset >= 0) {
         RuntimeBuffer buffer = (RuntimeBuffer)object;
         buffer.nextRef = (RuntimeBuffer)updatePointer(buffer.nextRef.toAddress()).toStructure();
      }
   }

   private static void updatePointersInArray(RuntimeClass cls, RuntimeArray array) {
      if (!RuntimeClass.isPrimitive(cls.itemType)) {
         Address base = Address.align(array.toAddress().add(RuntimeArray.class, 1), Address.sizeOf());
         int size = array.size;

         for (int i = 0; i < size; i++) {
            base.putAddress(updatePointer(base.getAddress()));
            base = base.add(Address.sizeOf());
         }
      }
   }

   private static void updatePointersFromStringPool() {
      Mutator.stringHashtableRewind();

      while (Mutator.stringHashtableCurrent() != null) {
         Mutator.stringHashtableUpdateRef((RuntimeObject)updatePointer(Mutator.stringHashtableCurrent().toAddress()).toStructure());
         Mutator.stringHashtableNext();
      }
   }

   private static Address updatePointer(Address address) {
      if (address == null) {
         return null;
      }

      Relocation relocation = getRelocation(address);
      return relocation != null ? relocation.newAddress : address;
   }

   private static Relocation getRelocation(Address address) {
      if (!address.isLessThan(heapAddress()) && address.isLessThan(heapAddress().add(availableBytes()))) {
         FreeChunk obj = (FreeChunk)address.toStructure();
         if ((obj.classReference & -2147483648) == 0) {
            return null;
         }

         long result = (obj.classReference & 4294967295L) << 33 | (obj.size & 4294967295L) << 1;
         return (Relocation)Address.fromLong(result).toStructure();
      } else {
         return null;
      }
   }

   private static void restoreObjectHeaders() {
      int relocatableMask = 134744072;
      int regionsCount = getRegionCount();
      Address cardPtr = cardTable();
      Address limit = heapAddress().add(availableBytes());

      int regionIndex;
      for (regionIndex = 0; regionIndex < regionsCount - 3; regionIndex += 4) {
         int n = cardPtr.getInt();
         if ((n & relocatableMask) != 0) {
            for (int i = 0; i < 4; i++) {
               int var7 = cardPtr.add(i).getByte();
               if ((var7 & 8) != 0) {
                  restoreObjectHeadersInRegion(regionIndex + i, limit);
               }
            }
         }

         cardPtr = cardPtr.add(4);
      }

      while (regionIndex < regionsCount) {
         if ((cardPtr.getByte() & 8) != 0) {
            restoreObjectHeadersInRegion(regionIndex, limit);
         }

         cardPtr = cardPtr.add(1);
         regionIndex++;
      }
   }

   private static void restoreObjectHeadersInRegion(int region, Address limit) {
      int regionOffset = ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), region)).start - 1;
      int regionSize = regionSize();
      Address regionStart = heapAddress().add(region * regionSize);
      Address regionEnd = regionStart.add(regionSize);
      FreeChunk object = (FreeChunk)regionStart.add(regionOffset).toStructure();
      if (limit.isLessThan(regionEnd)) {
         regionEnd = limit;
      }

      restoreObjectHeadersInRange(object, regionEnd);
   }

   private static void restoreObjectHeadersInRange(FreeChunk object, Address limit) {
      while (object.toAddress().isLessThan(limit)) {
         Relocation relocation = getRelocation(object.toAddress());
         if (relocation != null) {
            object.classReference = relocation.classBackup | -2147483648;
            object.size = relocation.sizeBackup;
         }

         int size = objectSize(object);
         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }
   }

   private static void relocateObjects() {
      Address start = heapAddress();
      long heapSize = availableBytes();
      Address limit = start.add(heapSize);
      int freeChunks = GC.freeChunks;
      FreeChunk freeChunk = currentChunkPointer.value;
      FreeChunk object = (FreeChunk)freeChunk.toAddress().add(freeChunk.size).toStructure();
      RelocationBlock relocationBlock = (RelocationBlock)((FreeChunkHolder)Structure.add(FreeChunkHolder.class, currentChunkPointer, freeChunks))
         .toAddress()
         .toStructure();
      int countInRelocationBlock = relocationBlock.count;
      Address relocationTarget = relocationBlock.start;
      Address blockTarget = null;
      Address blockSource = null;
      int blockSize = 0;
      Address currentRegionEnd = null;
      int regionCount = getRegionCount();

      while (object.toAddress().isLessThan(limit)) {
         int size = objectSize(object);
         if ((object.classReference & -2147483648) != 0) {
            for (object.classReference &= Integer.MAX_VALUE; countInRelocationBlock == 0; relocationTarget = relocationBlock.start) {
               if (blockSize != 0) {
                  moveMemoryBlock(blockSource, blockTarget, blockSize);
                  blockSource = null;
                  blockSize = 0;
               }

               relocationBlock.start = relocationTarget;
               relocationBlock = (RelocationBlock)Structure.add(RelocationBlock.class, relocationBlock, 1);
               countInRelocationBlock = relocationBlock.count;
            }

            if (blockSource == null) {
               blockSource = object.toAddress();
               blockTarget = relocationTarget;
            }

            relocationTarget = relocationTarget.add(size);
            blockSize += size;
            countInRelocationBlock--;
         } else {
            if (blockSource != null) {
               moveMemoryBlock(blockSource, blockTarget, blockSize);
               blockSource = null;
               blockSize = 0;
            }

            if (object.classReference != 0 && !object.toAddress().isLessThan(currentRegionEnd)) {
               int region = (int)((object.toAddress().toLong() - heapAddress().toLong()) / regionSize());
               currentRegionEnd = heapAddress().add((long)regionSize() * (region + 1));
               byte card = cardTable().add(region).getByte();
               if ((card & 8) == 0 && (card & 4) == 0) {
                  while (++region < regionCount) {
                     card = cardTable().add(region).getByte();
                     if ((card & 8) != 0 || (card & 4) != 0 || ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), region)).start == 0) {
                        break;
                     }
                  }

                  currentRegionEnd = heapAddress().add((long)regionSize() * (--region + 1));
                  int offset = ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), region)).start - 1;
                  object = (FreeChunk)heapAddress().add((long)regionSize() * region).add(offset).toStructure();
                  size = objectSize(object);
               }
            }
         }

         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }

      relocationBlock.start = relocationTarget;
      if (blockSource != null) {
         moveMemoryBlock(blockSource, blockTarget, blockSize);
      }
   }

   private static void moveMemoryBlock(Address blockSource, Address blockTarget, int blockSize) {
      long sourceStartOffset = blockSource.toLong() - heapAddress().toLong();
      int sourceStartRegionIndex = (int)(sourceStartOffset / regionSize());
      GC.Region sourceStartRegion = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), sourceStartRegionIndex);
      long sourceEndOffset = sourceStartOffset + blockSize;
      int sourceEndRegionIndex = (int)(sourceEndOffset / regionSize());
      GC.Region sourceEndRegion = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), sourceEndRegionIndex);
      if (sourceStartRegion != sourceEndRegion && sourceStartOffset % regionSize() + 1L == sourceStartRegion.start) {
         sourceStartRegion.start = 0;
      }

      for (int i = sourceStartRegionIndex + 1; i < sourceEndRegionIndex; i++) {
         ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), i)).start = 0;
      }

      if (sourceStartRegion != sourceEndRegion || sourceStartOffset % regionSize() + 1L == sourceStartRegion.start) {
         Address heapLimit = heapAddress().add(availableBytes());
         FreeChunk objectAfterSource = (FreeChunk)blockSource.add(blockSize).toStructure();
         if (objectAfterSource.toAddress().isLessThan(heapLimit)) {
            int objectRegionIndex = sourceEndRegionIndex;
            if (objectAfterSource.classReference == 0) {
               objectAfterSource = (FreeChunk)objectAfterSource.toAddress().add(objectAfterSource.size).toStructure();
               objectRegionIndex = (int)((objectAfterSource.toAddress().toLong() - heapAddress().toLong()) / regionSize());
            }

            if (objectRegionIndex == sourceEndRegionIndex && objectAfterSource.toAddress().isLessThan(heapLimit)) {
               sourceEndRegion.start = (short)((objectAfterSource.toAddress().toLong() - heapAddress().toLong()) % regionSize() + 1L);
            } else {
               sourceEndRegion.start = 0;
            }
         } else {
            sourceEndRegion.start = 0;
         }
      }

      Address.moveMemoryBlock(blockSource, blockTarget, blockSize);
      FreeChunk object = (FreeChunk)blockTarget.toStructure();
      Address blockTargetEnd = blockTarget.add(blockSize);
      Address currentRegionEnd = null;

      while (object.toAddress().isLessThan(blockTargetEnd)) {
         if (!object.toAddress().isLessThan(currentRegionEnd)) {
            long offset = object.toAddress().toLong() - heapAddress().toLong();
            int regionIndex = (int)(offset / regionSize());
            currentRegionEnd = heapAddress().add((long)regionSize() * (regionIndex + 1));
            GC.Region region = (GC.Region)Structure.add(GC.Region.class, regionsAddress(), regionIndex);
            int offsetInRegion = (int)(offset % regionSize());
            if (region.start == 0 || region.start - 1 > offsetInRegion) {
               region.start = (short)(offsetInRegion + 1);
            }
         }

         int size = objectSize(object);
         object = (FreeChunk)object.toAddress().add(size).toStructure();
      }

      MemoryTrace.move(blockSource, blockTarget, blockSize);
   }

   private static void putNewFreeChunks() {
      FreeChunkHolder freeChunkPointer = currentChunkPointer;
      RelocationBlock relocationBlock = (RelocationBlock)((FreeChunkHolder)Structure.add(FreeChunkHolder.class, currentChunkPointer, freeChunks))
         .toAddress()
         .toStructure();

      for (freeChunks = 0;
         !lastRelocationBlock.toAddress().isLessThan(relocationBlock.toAddress());
         relocationBlock = (RelocationBlock)Structure.add(RelocationBlock.class, relocationBlock, 1)
      ) {
         if (relocationBlock.start.isLessThan(relocationBlock.end)) {
            FreeChunk freeChunk = (FreeChunk)relocationBlock.start.toStructure();
            if (!freeChunk.toAddress().isLessThan(lastChunk.toAddress())) {
               lastChunk = freeChunk;
            }

            freeChunk.size = (int)(relocationBlock.end.toLong() - relocationBlock.start.toLong());
            freeChunk.classReference = 0;
            MemoryTrace.assertFree(freeChunk.toAddress(), freeChunk.size);
            freeChunkPointer.value = freeChunk;
            freeChunkPointer = (FreeChunkHolder)Structure.add(FreeChunkHolder.class, freeChunkPointer, 1);
            freeChunks++;
         }
      }

      totalChunks = freeChunks;
   }

   private static void updateFreeMemory() {
      freeMemory = 0;
      FreeChunkHolder freeChunkPtr = currentChunkPointer;

      for (int i = 0; i < freeChunks; i++) {
         freeMemory = freeMemory + freeChunkPtr.value.size;
         freeChunkPtr = (FreeChunkHolder)Structure.add(FreeChunkHolder.class, freeChunkPtr, 1);
      }
   }

   private static void resizeHeapConsistent(long newSize) {
      long oldSize = availableBytes();
      if (newSize != oldSize) {
         if (newSize > oldSize) {
            int previousRegionCount = getRegionCount();
            resizeHeap(newSize);
            currentChunkPointer = (FreeChunkHolder)gcStorageAddress().toStructure();
            int newRegionCount = getRegionCount();

            for (int i = previousRegionCount; i < newRegionCount; i++) {
               ((GC.Region)Structure.add(GC.Region.class, regionsAddress(), i)).start = 0;
            }

            if (lastChunk.classReference == 0) {
               lastChunk.size += (int)(newSize - oldSize);
            } else {
               int size = objectSize(lastChunk);
               lastChunk = (FreeChunk)lastChunk.toAddress().add(size).toStructure();
               lastChunk.classReference = 0;
               lastChunk.size = (int)(newSize - oldSize);
               ((FreeChunkHolder)Structure.add(FreeChunkHolder.class, currentChunkPointer, freeChunks)).value = lastChunk;
               freeChunks++;
               totalChunks++;
            }
         } else if (canShrinkHeap()) {
            long minimumSize = lastChunk.toAddress().toLong() - heapAddress().toLong();
            if (lastChunk.classReference != 0) {
               minimumSize += objectSize(lastChunk);
            }

            if (newSize < minimumSize) {
               newSize = minimumSize;
               if (newSize == oldSize) {
                  return;
               }
            }

            if (newSize == minimumSize) {
               freeChunks--;
               totalChunks--;
            } else {
               lastChunk.size -= (int)(oldSize - newSize);
            }

            resizeHeap(newSize);
            currentChunkPointer = (FreeChunkHolder)gcStorageAddress().toStructure();
         }
      }
   }

   private static void resizeHeapIfNecessary(long requestedSize) {
      long availableBytes = availableBytes();
      long occupiedMemory = availableBytes - freeMemory;
      if (isAboutToExpand(requestedSize)) {
         long newSize = max(requestedSize, (availableBytes - freeMemory) * 2L);
         newSize = min(newSize, maxAvailableBytes());
         if (newSize != availableBytes) {
            if (newSize % 8L != 0L) {
               newSize += 8L - newSize % 8L;
            }

            resizeHeapConsistent(newSize);
         }
      } else if (occupiedMemory < availableBytes / 4L) {
         long newSize = occupiedMemory * 3L;
         newSize = max(newSize, minAvailableBytes());
         if (newSize % 8L != 0L) {
            newSize -= newSize % 8L;
         }

         resizeHeapConsistent(newSize);
      }
   }

   private static boolean isAboutToExpand(long requestedSize) {
      long availableBytes = availableBytes();
      long occupiedMemory = availableBytes - freeMemory;
      return requestedSize > availableBytes || occupiedMemory > availableBytes / 2L;
   }

   private static long min(long a, long b) {
      return a < b ? a : b;
   }

   private static long max(long a, long b) {
      return a > b ? a : b;
   }

   private static int objectSize(FreeChunk object) {
      if (object.classReference == 0) {
         return object.size;
      }

      RuntimeObject realObject = (RuntimeObject)object.toAddress().toStructure();
      RuntimeClass cls = RuntimeClass.getClass(realObject);
      return objectSize(realObject, cls);
   }

   private static int objectSize(RuntimeObject object, RuntimeClass cls) {
      if (cls.itemType == null) {
         return cls.size;
      }

      int itemSize = !RuntimeClass.isPrimitive(cls.itemType) ? Address.sizeOf() : cls.itemType.size;
      RuntimeArray array = (RuntimeArray)object.toAddress().toStructure();
      Address address = Address.fromInt(Structure.sizeOf(RuntimeArray.class));
      address = Address.align(address, itemSize);
      address = address.add(itemSize * array.size);
      address = Address.align(address, Address.sizeOf());
      return address.toInt();
   }

   private static boolean isMarked(RuntimeObject object) {
      return (object.classReference & -2147483648) != 0 || !isFullGC && (object.classReference & 1073741824) != 0;
   }

   private static void free(Address address) {
      if (PlatformDetector.isC()) {
         Memory.free(address);
      }
   }

   static {
      currentChunk.classReference = 0;
      currentChunk.size = (int)availableBytes();
      currentChunkPointer.value = currentChunk;
      int regionCount = getRegionCount();
      Address.fill(cardTable(), (byte)1, regionCount);
   }

   static class Region extends Structure {
      short start;
   }
}
