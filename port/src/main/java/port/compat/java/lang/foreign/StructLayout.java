package port.compat.java.lang.foreign;
import java.util.*;
public final class StructLayout extends MemoryLayout {
    private final MemoryLayout[] members;
    public StructLayout(MemoryLayout[] members){super(size(members),alignment(members));this.members=members.clone();long offset=0;for(var m:members){if(offset%m.byteAlignment()!=0)throw new IllegalArgumentException("Misaligned struct member");offset+=m.byteSize();}}
    private static long size(MemoryLayout[] members){long size=0;for(var m:members)size=Math.addExact(size,m.byteSize());return size;}
    private static long alignment(MemoryLayout[] members){long a=1;for(var m:members)a=Math.max(a,m.byteAlignment());return a;}
    public StructLayout withName(String name){var copy=new StructLayout(members);copy.name=Objects.requireNonNull(name);return copy;}
    public long byteOffset(PathElement... path){long offset=0;StructLayout current=this;for(int i=0;i<path.length;i++){boolean found=false;for(var m:current.members){if(path[i].name.equals(m.name)){found=true;if(i+1<path.length){if(!(m instanceof StructLayout s))throw new IllegalArgumentException("Not a group layout");current=s;}break;}offset+=m.byteSize();}if(!found)throw new IllegalArgumentException("Unknown layout member "+path[i].name);}return offset;}
    public port.compat.java.lang.invoke.VarHandle varHandle(PathElement... path){throw new UnsupportedOperationException("MemoryLayout.varHandle: use checked MemorySegment get/set; layout handles are not integrated");}
}
