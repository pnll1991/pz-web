package port.compat.java.lang.foreign;
public final class SequenceLayout extends MemoryLayout {
    private final long count;private final MemoryLayout element;
    SequenceLayout(long count,MemoryLayout element){super(Math.multiplyExact(count,element.byteSize()),element.byteAlignment());if(count<0)throw new IllegalArgumentException();this.count=count;this.element=element;}
    public SequenceLayout withName(String name){var copy=new SequenceLayout(count,element);copy.name=java.util.Objects.requireNonNull(name);return copy;}
}
