package port.runtime.memory;

import java.nio.*;
import java.util.*;

/** Checked virtual addresses. Never exposes JVM/GC object addresses or accepts foreign pointers. */
public final class NativeArena {
    private NativeArena() { }
    private static final TreeMap<Long, Block> blocks = new TreeMap<>();
    private static final IdentityHashMap<Buffer, View> views = new IdentityHashMap<>();
    private static final List<java.lang.ref.WeakReference<Buffer>> retired=new ArrayList<>();
    private static long next = 65536;
    // Last block hit by block(): per-byte access (TileDepthTexture.load, Texture uploads) hits the
    // same block millions of times; the TreeMap floor lookup per byte dominated the CPU profile
    // (menu-r65-prof-profile.txt). Same bounds check, cleared when that block is freed.
    private static Block last;
    private record Block(long address, ByteBuffer bytes, ByteBuffer backing) { }
    // Reuse pool for freed large blocks. TeaVM Wasm GC only returns direct-buffer memory to its Heap from a
    // JS FinalizationRegistry callback, i.e. long after free() and only between tasks. During boot the
    // 1024x11008 depthmaps (~45 MB each) piled up until linear memory hit its 2 GiB cap; TeaVM 0.16's
    // Heap.grow drops the memory.grow result, so the failed grow surfaced as "memory access out of bounds"
    // in Heap.allocFromFreeRecord (ronda 66/67). Freed backings are kept and handed out again (zeroed).
    private static final int POOL_MIN = 1 << 16;
    private static final long POOL_CAP = 768L << 20;
    private static final TreeMap<Integer, ArrayDeque<ByteBuffer>> pool = new TreeMap<>();
    private static long pooledBytes;
    private static final byte[] ZEROS = new byte[1 << 16];
    private static ByteBuffer takePooled(int size) {
        var e = pool.ceilingEntry(size);
        if (e == null || e.getKey() > size + (size >> 2)) return null;
        ByteBuffer backing = e.getValue().pop(); if (e.getValue().isEmpty()) pool.remove(e.getKey());
        pooledBytes -= backing.capacity();
        return backing;
    }
    private record View(Block block, int offset, int stride) { }
    public static synchronized long allocate(long size, long alignment) {
        if (size < 0 || size > Integer.MAX_VALUE) throw new IllegalArgumentException("Arena allocation size: " + size);
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0 || alignment > 1L << 30) throw new IllegalArgumentException("Arena alignment");
        long address = Math.addExact(next, alignment - 1) & -alignment;
        next = Math.addExact(address, Math.max(1, size) + 16);
        ByteBuffer backing = size >= POOL_MIN ? takePooled((int) size) : null;
        ByteBuffer bytes;
        if (backing != null) {
            var z = backing.duplicate().clear(); while (z.position() < size) z.put(ZEROS, 0, (int) Math.min(ZEROS.length, size - z.position()));
            bytes = backing.duplicate().clear().limit((int) size).slice().order(ByteOrder.LITTLE_ENDIAN);
        } else {
            bytes = ByteBuffer.allocateDirect((int) size).order(ByteOrder.LITTLE_ENDIAN);
            backing = size >= POOL_MIN ? bytes : null;
        }
        var block = new Block(address, bytes, backing); blocks.put(address, block); views.put(bytes, new View(block, 0, 1));
        return address;
    }
    public static synchronized void free(long address) {
        if (address == 0) return;
        Block block = blocks.remove(address);
        if (block == null) throw new IllegalArgumentException("Arena free requires a live allocation base: " + address);
        if (last == block) last = null;
        if (block.backing != null && pooledBytes + block.backing.capacity() <= POOL_CAP) {
            pool.computeIfAbsent(block.backing.capacity(), k -> new ArrayDeque<>()).push(block.backing);
            pooledBytes += block.backing.capacity();
        }
        views.entrySet().removeIf(e -> {if(e.getValue().block != block)return false;retired.add(new java.lang.ref.WeakReference<>(e.getKey()));return true;});
    }
    public static synchronized boolean live(long address, long size) {
        if (size < 0) return false;
        var entry = blocks.floorEntry(address);
        return entry != null && address - entry.getKey() <= entry.getValue().bytes.capacity() - size;
    }
    private static Block block(long address, long size) {
        Block hit = last;
        if (hit != null && size >= 0 && address >= hit.address && address - hit.address <= hit.bytes.capacity() - size) return hit;
        if (!live(address, size)) throw new IllegalStateException("Invalid/freed arena address or bounds: " + address + " + " + size);
        return last = blocks.floorEntry(address).getValue();
    }
    public static synchronized ByteBuffer buffer(long address, int size) {
        var block = block(address, size); int offset = Math.toIntExact(address - block.address);
        var bytes = block.bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        bytes.position(offset).limit(offset + size);
        var view = bytes.slice().order(ByteOrder.LITTLE_ENDIAN); views.put(view,new View(block,offset,1)); return view;
    }
    public static synchronized <T extends Buffer> T registerView(long address, T buffer, int stride) {
        var block = block(address, (long) buffer.capacity() * stride);
        views.put(buffer,new View(block,Math.toIntExact(address-block.address),stride)); return buffer;
    }
    public static synchronized long address(Buffer buffer, int position) {
        Objects.requireNonNull(buffer);
        if (position < 0 || position > buffer.capacity()) throw new IndexOutOfBoundsException("Buffer position");
        for(var iterator=retired.iterator();iterator.hasNext();){Buffer old=iterator.next().get();if(old==null)iterator.remove();else if(old==buffer)throw new IllegalStateException("Buffer allocation was freed");}
        View view = views.get(buffer);
        if (view == null) {
            if (!(buffer instanceof ByteBuffer bytes) || !bytes.isDirect())
                throw new UnsupportedOperationException("Arena address requires a registered/direct ByteBuffer view");
            long address = Math.addExact(next, 15) & -16L; next = Math.addExact(address, Math.max(1, bytes.capacity()) + 16);
            var backing = bytes.duplicate().clear().order(ByteOrder.LITTLE_ENDIAN);
            Block block = new Block(address,backing,null); blocks.put(address,block);
            view = new View(block,0,1); views.put(buffer,view);
        }
        if (!blocks.containsKey(view.block.address)) throw new IllegalStateException("Buffer allocation was freed");
        return view.block.address + view.offset + (long) position * view.stride;
    }
    public static long address(Buffer buffer) { return address(buffer,buffer.position()); }
    public static synchronized void copy(long source, long target, long length) {
        if (length < 0 || length > Integer.MAX_VALUE) throw new IllegalArgumentException("Arena copy size");
        if (length == 0) return;
        // Bulk memmove (was one get/put per byte: 45 MB depthmaps took minutes in wasm). Overlap goes
        // through a temporary array, which gives memmove semantics.
        ByteBuffer from = buffer(source,(int) length), to = buffer(target,(int) length);
        byte[] tmp = new byte[(int) length]; from.get(0, tmp); to.put(0, tmp);
    }
    public static synchronized void putBytes(long address, byte[] data, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, data.length); if (length == 0) return;
        Block b = block(address, length); b.bytes.put(Math.toIntExact(address - b.address), data, offset, length);
    }
    public static synchronized void getBytes(long address, byte[] data, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, data.length); if (length == 0) return;
        Block b = block(address, length); b.bytes.get(Math.toIntExact(address - b.address), data, offset, length);
    }
    public static synchronized void fill(long address, long length, byte value) {
        if (length == 0) return;
        var data = buffer(address,Math.toIntExact(length)); byte[] chunk = new byte[(int) Math.min(length, 1 << 16)];
        if (value != 0) Arrays.fill(chunk, value);
        while (data.hasRemaining()) data.put(chunk, 0, Math.min(chunk.length, data.remaining()));
    }
    public static synchronized long reallocate(long address, long size) {
        if (address == 0) return allocate(size,8);
        Block old = block(address,0);
        if (old.address != address) throw new IllegalArgumentException("Arena realloc requires allocation base");
        if (size == 0) { free(address); return 0; }
        long result = allocate(size,8); copy(address,result,Math.min(size,old.bytes.capacity())); free(address); return result;
    }
    public static synchronized byte getByte(long p) { Block b=block(p,1); return b.bytes.get((int)(p-b.address)); }
    public static synchronized void putByte(long p, byte v) { Block b=block(p,1); b.bytes.put((int)(p-b.address),v); }
    public static synchronized short getShort(long p) { Block b=block(p,2); return b.bytes.getShort((int)(p-b.address)); }
    public static synchronized void putShort(long p, short v) { Block b=block(p,2); b.bytes.putShort((int)(p-b.address),v); }
    public static synchronized int getInt(long p) { Block b=block(p,4); return b.bytes.getInt((int)(p-b.address)); }
    public static synchronized void putInt(long p, int v) { Block b=block(p,4); b.bytes.putInt((int)(p-b.address),v); }
    public static synchronized long getLong(long p) { Block b=block(p,8); return b.bytes.getLong((int)(p-b.address)); }
    public static synchronized void putLong(long p, long v) { Block b=block(p,8); b.bytes.putLong((int)(p-b.address),v); }
    public static float getFloat(long p) { return Float.intBitsToFloat(getInt(p)); }
    public static void putFloat(long p,float v) { putInt(p,Float.floatToRawIntBits(v)); }
    public static double getDouble(long p) { return Double.longBitsToDouble(getLong(p)); }
    public static void putDouble(long p,double v) { putLong(p,Double.doubleToRawLongBits(v)); }
    public static char getChar(long p) { return (char) getShort(p); }
    public static void putChar(long p,char v) { putShort(p,(short)v); }
    public static synchronized long allocatedBytes() { long n=0; for(var b:blocks.values()) n+=b.bytes.capacity(); return n; }
}
