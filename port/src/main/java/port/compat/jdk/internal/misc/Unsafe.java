package port.compat.jdk.internal.misc;
public final class Unsafe extends port.compat.sun.misc.Unsafe {
    private static final Unsafe INSTANCE=new Unsafe();
    public static Unsafe getUnsafe(){return INSTANCE;}
}
