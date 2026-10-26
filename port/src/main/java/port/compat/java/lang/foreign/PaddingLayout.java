package port.compat.java.lang.foreign;
public final class PaddingLayout extends MemoryLayout {
    PaddingLayout(long size){super(size,1);if(size==0)throw new IllegalArgumentException();}
    public PaddingLayout withName(String name){var copy=new PaddingLayout(size);copy.name=java.util.Objects.requireNonNull(name);return copy;}
}
