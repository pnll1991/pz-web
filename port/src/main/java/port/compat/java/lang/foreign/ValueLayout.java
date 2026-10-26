package port.compat.java.lang.foreign;
public abstract class ValueLayout extends MemoryLayout {
    protected ValueLayout(int size){super(size,size);}
    public static final AddressLayout ADDRESS=new AddressLayout();
    public static final OfBoolean JAVA_BOOLEAN=new OfBoolean();
    public static final class OfBoolean extends ValueLayout {
        public OfBoolean(){super(1);}
        public OfBoolean withName(String name){var copy=new OfBoolean();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
    public static final OfByte JAVA_BYTE=new OfByte();
    public static final class OfByte extends ValueLayout {
        public OfByte(){super(1);}
        public OfByte withName(String name){var copy=new OfByte();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
    public static final OfShort JAVA_SHORT=new OfShort();
    public static final class OfShort extends ValueLayout {
        public OfShort(){super(2);}
        public OfShort withName(String name){var copy=new OfShort();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
    public static final OfChar JAVA_CHAR=new OfChar();
    public static final class OfChar extends ValueLayout {
        public OfChar(){super(2);}
        public OfChar withName(String name){var copy=new OfChar();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
    public static final OfInt JAVA_INT=new OfInt();
    public static final class OfInt extends ValueLayout {
        public OfInt(){super(4);}
        public OfInt withName(String name){var copy=new OfInt();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
    public static final OfLong JAVA_LONG=new OfLong();
    public static final class OfLong extends ValueLayout {
        public OfLong(){super(8);}
        public OfLong withName(String name){var copy=new OfLong();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
    public static final OfFloat JAVA_FLOAT=new OfFloat();
    public static final class OfFloat extends ValueLayout {
        public OfFloat(){super(4);}
        public OfFloat withName(String name){var copy=new OfFloat();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
    public static final OfDouble JAVA_DOUBLE=new OfDouble();
    public static final class OfDouble extends ValueLayout {
        public OfDouble(){super(8);}
        public OfDouble withName(String name){var copy=new OfDouble();copy.name=java.util.Objects.requireNonNull(name);return copy;}
    }
}
