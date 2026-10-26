package port.compat.java.lang.foreign;
import java.util.*;
import port.runtime.memory.NativeArena;
public final class MemorySegment {
    public static final MemorySegment NULL=new MemorySegment(0,0,Arena.global());
    private final long address,size; private final Arena arena;
    MemorySegment(long address,long size,Arena arena){this.address=address;this.size=size;this.arena=arena;}
    public static MemorySegment ofAddress(long address){return address==0?NULL:new MemorySegment(address,0,Arena.global());}
    public long address(){return address;} public long byteSize(){return size;}
    private long check(long offset,long bytes,long alignment){arena.check();if(offset<0||bytes<0||offset>size-bytes)throw new IndexOutOfBoundsException("MemorySegment bounds");long p=Math.addExact(address,offset);if(p%alignment!=0)throw new IllegalArgumentException("Misaligned segment access");return p;}
    public MemorySegment asSlice(long offset){return asSlice(offset,size-offset);}
    public MemorySegment asSlice(long offset,long bytes){check(offset,bytes,1);return new MemorySegment(address+offset,bytes,arena);}
    public MemorySegment reinterpret(long bytes,Arena arena,java.util.function.Consumer<MemorySegment> cleanup){
        this.arena.check();arena.check();if(cleanup!=null)throw new UnsupportedOperationException("MemorySegment.reinterpret cleanup is not integrated");
        if(!NativeArena.live(address,bytes))throw new IllegalArgumentException("Only live browser arena allocations can be reinterpreted");return new MemorySegment(address,bytes,arena);
    }
    public MemorySegment fill(byte value){check(0,size,1);NativeArena.fill(address,size,value);return this;}
    public MemorySegment copyFrom(MemorySegment source){source.check(0,source.size,1);check(0,source.size,1);NativeArena.copy(source.address,address,source.size);return this;}
    public String getString(long offset){check(offset,0,1);var bytes=new java.io.ByteArrayOutputStream();for(long i=offset;i<size;i++){byte b=NativeArena.getByte(address+i);if(b==0)return new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);bytes.write(b);}throw new IndexOutOfBoundsException("Unterminated UTF-8 segment");}
    public MemorySegment get(AddressLayout layout,long offset){return ofAddress(NativeArena.getLong(check(offset,8,layout.byteAlignment())));}
    public void set(AddressLayout layout,long offset,MemorySegment value){NativeArena.putLong(check(offset,8,layout.byteAlignment()),Objects.requireNonNull(value).address());}
    public boolean equals(Object other){return other instanceof MemorySegment segment&&address==segment.address;}
    public int hashCode(){return Long.hashCode(address);}
    public int[] toArray(ValueLayout.OfInt layout){if(size%4!=0)throw new IllegalStateException("Nonintegral element count");int[] values=new int[Math.toIntExact(size/4)];for(int i=0;i<values.length;i++)values[i]=get(layout,i*4L);return values;}
    public static void copy(MemorySegment source,ValueLayout layout,long offset,Object target,int index,int count){
        int n=java.lang.reflect.Array.getLength(target);Objects.checkFromIndexSize(index,count,n);long bytes=Math.multiplyExact(layout.byteSize(),count);source.check(offset,bytes,layout.byteAlignment());
        for(int i=0;i<count;i++){long p=offset+i*layout.byteSize();if(layout instanceof ValueLayout.OfInt l&&target instanceof int[] a)a[index+i]=source.get(l,p);else if(layout instanceof ValueLayout.OfLong l&&target instanceof long[] a)a[index+i]=source.get(l,p);else if(layout instanceof ValueLayout.OfByte l&&target instanceof byte[] a)a[index+i]=source.get(l,p);else throw new IllegalArgumentException("Segment copy element layout does not match array");}
    }
    public boolean get(ValueLayout.OfBoolean layout,long offset){long p=check(offset,1,layout.byteAlignment());return NativeArena.getByte(p)!=0;}
    public void set(ValueLayout.OfBoolean layout,long offset,boolean value){long p=check(offset,1,layout.byteAlignment());NativeArena.putByte(p,(byte)(value?1:0));}
    public boolean getAtIndex(ValueLayout.OfBoolean layout,long index){return get(layout,Math.multiplyExact(index,1L));}
    public byte get(ValueLayout.OfByte layout,long offset){long p=check(offset,1,layout.byteAlignment());return NativeArena.getByte(p);}
    public void set(ValueLayout.OfByte layout,long offset,byte value){long p=check(offset,1,layout.byteAlignment());NativeArena.putByte(p,value);}
    public byte getAtIndex(ValueLayout.OfByte layout,long index){return get(layout,Math.multiplyExact(index,1L));}
    public short get(ValueLayout.OfShort layout,long offset){long p=check(offset,2,layout.byteAlignment());return NativeArena.getShort(p);}
    public void set(ValueLayout.OfShort layout,long offset,short value){long p=check(offset,2,layout.byteAlignment());NativeArena.putShort(p,value);}
    public short getAtIndex(ValueLayout.OfShort layout,long index){return get(layout,Math.multiplyExact(index,2L));}
    public char get(ValueLayout.OfChar layout,long offset){long p=check(offset,2,layout.byteAlignment());return NativeArena.getChar(p);}
    public void set(ValueLayout.OfChar layout,long offset,char value){long p=check(offset,2,layout.byteAlignment());NativeArena.putChar(p,value);}
    public char getAtIndex(ValueLayout.OfChar layout,long index){return get(layout,Math.multiplyExact(index,2L));}
    public int get(ValueLayout.OfInt layout,long offset){long p=check(offset,4,layout.byteAlignment());return NativeArena.getInt(p);}
    public void set(ValueLayout.OfInt layout,long offset,int value){long p=check(offset,4,layout.byteAlignment());NativeArena.putInt(p,value);}
    public int getAtIndex(ValueLayout.OfInt layout,long index){return get(layout,Math.multiplyExact(index,4L));}
    public long get(ValueLayout.OfLong layout,long offset){long p=check(offset,8,layout.byteAlignment());return NativeArena.getLong(p);}
    public void set(ValueLayout.OfLong layout,long offset,long value){long p=check(offset,8,layout.byteAlignment());NativeArena.putLong(p,value);}
    public long getAtIndex(ValueLayout.OfLong layout,long index){return get(layout,Math.multiplyExact(index,8L));}
    public float get(ValueLayout.OfFloat layout,long offset){long p=check(offset,4,layout.byteAlignment());return NativeArena.getFloat(p);}
    public void set(ValueLayout.OfFloat layout,long offset,float value){long p=check(offset,4,layout.byteAlignment());NativeArena.putFloat(p,value);}
    public float getAtIndex(ValueLayout.OfFloat layout,long index){return get(layout,Math.multiplyExact(index,4L));}
    public double get(ValueLayout.OfDouble layout,long offset){long p=check(offset,8,layout.byteAlignment());return NativeArena.getDouble(p);}
    public void set(ValueLayout.OfDouble layout,long offset,double value){long p=check(offset,8,layout.byteAlignment());NativeArena.putDouble(p,value);}
    public double getAtIndex(ValueLayout.OfDouble layout,long index){return get(layout,Math.multiplyExact(index,8L));}
}
