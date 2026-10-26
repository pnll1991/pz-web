package port.compat.java.lang.foreign;
import java.util.*;
public abstract class MemoryLayout {
    final long size,alignment;String name;
    protected MemoryLayout(long size,long alignment){if(size<0||alignment<=0||(alignment&(alignment-1))!=0)throw new IllegalArgumentException("Memory layout");this.size=size;this.alignment=alignment;}
    public long byteSize(){return size;} public long byteAlignment(){return alignment;} public Optional<String> name(){return Optional.ofNullable(name);}
    public abstract MemoryLayout withName(String name);
    public static StructLayout structLayout(MemoryLayout... members){return new StructLayout(members);}
    public static SequenceLayout sequenceLayout(long count,MemoryLayout element){return new SequenceLayout(count,element);}
    public static PaddingLayout paddingLayout(long bytes){return new PaddingLayout(bytes);}
    public static final class PathElement { final String name;private PathElement(String name){this.name=Objects.requireNonNull(name);}public static PathElement groupElement(String name){return new PathElement(name);} }
}
