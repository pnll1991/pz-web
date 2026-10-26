package port.compat.java.lang.foreign;
import java.nio.charset.*;
import java.util.*;
import port.runtime.memory.NativeArena;
public final class Arena implements AutoCloseable {
    private static final Arena GLOBAL=new Arena(null,false);
    private final Thread owner;private final boolean closeable;
    private final List<Long> allocations=new ArrayList<>();private boolean closed;
    private Arena(Thread owner,boolean closeable){this.owner=owner;this.closeable=closeable;}
    public static Arena global(){return GLOBAL;}
    public static Arena ofConfined(){return new Arena(Thread.currentThread(),true);}
    public static Arena ofShared(){return new Arena(null,true);}
    public static Arena ofAuto(){throw new UnsupportedOperationException("Arena.ofAuto: automatic native allocation reclamation is not integrated; use a closeable arena");}
    void check(){if(closed)throw new IllegalStateException("Arena is closed");if(owner!=null&&owner!=Thread.currentThread())throw new port.compat.java.lang.WrongThreadException();}
    public synchronized MemorySegment allocate(long size){return allocate(size,1);}
    public synchronized MemorySegment allocate(long size,long alignment){check();long address=NativeArena.allocate(size,alignment);allocations.add(address);return new MemorySegment(address,size,this);}
    public MemorySegment allocate(MemoryLayout layout){return allocate(layout.byteSize(),layout.byteAlignment());}
    public MemorySegment allocate(MemoryLayout layout,long count){if(count<0)throw new IllegalArgumentException();return allocate(Math.multiplyExact(layout.byteSize(),count),layout.byteAlignment());}
    public MemorySegment allocateFrom(String value){return allocateFrom(value,StandardCharsets.UTF_8);}
    public MemorySegment allocateFrom(String value,Charset charset){byte[] bytes=value.getBytes(charset);int terminator=charset.name().startsWith("UTF-16")?2:1;var segment=allocate(bytes.length+terminator);for(int i=0;i<bytes.length;i++)NativeArena.putByte(segment.address()+i,bytes[i]);return segment;}
    public MemorySegment allocateFrom(ValueLayout.OfChar layout,char... values){var segment=allocate(layout,values.length);for(int i=0;i<values.length;i++)segment.set(layout,(long)i*2,values[i]);return segment;}
    public MemorySegment allocateFrom(ValueLayout.OfInt layout,int... values){var segment=allocate(layout,values.length);for(int i=0;i<values.length;i++)segment.set(layout,(long)i*4,values[i]);return segment;}
    public MemorySegment allocateFrom(ValueLayout.OfLong layout,long value){var segment=allocate(layout);segment.set(layout,0,value);return segment;}
    @Override public synchronized void close(){check();if(!closeable)throw new UnsupportedOperationException("Global arena cannot be closed");closed=true;for(long address:allocations)NativeArena.free(address);allocations.clear();}
}
