package port.demo;

public class ReflectionProbe {
    public String label = "initial";
    public int number = 3;
    public String value() { return "reflection-ok"; }
    public static final class Child extends ReflectionProbe {
        @Override public String value() { return "reflection-child-ok"; }
    }
}
