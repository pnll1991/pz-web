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
    private record Block(long address, ByteBuffer bytes) { }
    private record View(Block block, int offset, int stride) { }
    public static synchronized long allocate(long size, long alignment) {
        if (size < 0 || size > Integer.MAX_VALUE) throw new IllegalArgumentException("Arena allocation size: " + size);
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0 || alignment > 1L << 30) throw new IllegalArgumentException("Arena alignment");
        long address = Math.addExact(next, alignment - 1) & -alignment;
        next = Math.addExact(address, Math.max(1, size) + 16);
        var bytes = ByteBuffer.allocateDirect((int) size).order(ByteOrder.LITTLE_ENDIAN);
        var block = new Block(address, bytes); blocks.put(address, block); views.put(bytes, new View(block, 0, 1));
        return address;
    }
    public static synchronized void free(long address) {
        if (address == 0) return;
        Block block = blocks.remove(address);
        if (block == null) throw new IllegalArgumentException("Arena free requires a live allocation base: " + address);
        views.entrySet().removeIf(e -> {if(e.getValue().block != block)return false;retired.add(new java.lang.ref.WeakReference<>(e.getKey()));return true;});
    }
    public static synchronized boolean live(long address, long size) {
        if (size < 0) return false;
        var entry = blocks.floorEntry(address);
        return entry != null && address - entry.getKey() <= entry.getValue().bytes.capacity() - size;
    }
    private static Block block(long address, long size) {
        if (!live(address, size)) throw new IllegalStateException("Invalid/freed arena address or bounds: " + address + " + " + size);
        return blocks.floorEntry(address).getValue();
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
            Block block = new Block(address,backing); blocks.put(address,block);
            view = new View(block,0,1); views.put(buffer,view);
        }
        if (!blocks.containsKey(view.block.address)) throw new IllegalStateException("Buffer allocation was freed");
        return view.block.address + view.offset + (long) position * view.stride;
    }
    public static long address(Buffer buffer) { return address(buffer,buffer.position()); }
    public static synchronized void copy(long source, long target, long length) {
        if (length < 0 || length > Integer.MAX_VALUE) throw new IllegalArgumentException("Arena copy size");
        // memmove semantics for overlap; bounded temporary rather than corrupting aliases.
        ByteBuffer from = buffer(source,(int) length), to = buffer(target,(int) length);
        if (target > source && target - source < length) for (int i=(int)length-1;i>=0;i--) to.put(i,from.get(i));
        else for (int i=0;i<length;i++) to.put(i,from.get(i));
    }
    public static synchronized void fill(long address, long length, byte value) {
        var data = buffer(address,Math.toIntExact(length)); for (int i=0;i<data.capacity();i++) data.put(i,value);
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
