package port.compat.java.lang.foreign;
public final class AddressLayout extends ValueLayout {
    AddressLayout(){super(8);}
    public AddressLayout withName(String name){var copy=new AddressLayout();copy.name=java.util.Objects.requireNonNull(name);return copy;}
}
