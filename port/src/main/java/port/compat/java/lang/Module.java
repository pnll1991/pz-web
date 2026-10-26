package port.compat.java.lang;
/** This AOT application is an unnamed module; it has no host native access. */
public final class Module {
    public String getName(){return null;}public boolean isNamed(){return false;}public boolean isNativeAccessEnabled(){return false;}
    public ClassLoader getClassLoader(){return ClassLoader.getSystemClassLoader();}
}
