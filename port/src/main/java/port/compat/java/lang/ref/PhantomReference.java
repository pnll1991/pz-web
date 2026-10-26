package port.compat.java.lang.ref;
/** Host-JVM native cleaners must use explicit browser resource lifetimes. */
public class PhantomReference<T> extends java.lang.ref.WeakReference<T> {
    public PhantomReference(T value,java.lang.ref.ReferenceQueue<? super T> queue){super(null);throw new UnsupportedOperationException("PhantomReference: host native cleanup is not integrated; close browser resources explicitly");}
    @Override public T get(){return null;}
}
