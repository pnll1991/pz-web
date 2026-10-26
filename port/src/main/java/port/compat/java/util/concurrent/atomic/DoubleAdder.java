package port.compat.java.util.concurrent.atomic;
public class DoubleAdder extends Number {
    private double value;
    public synchronized void add(double delta) { value+=delta; }
    public synchronized double sum() { return value; }
    public synchronized void reset() { value=0; }
    public synchronized double sumThenReset() { double previous=value;value=0;return previous; }
    public long longValue() { return (long)sum(); } public int intValue() { return (int)sum(); }
    public float floatValue() { return (float)sum(); } public double doubleValue() { return sum(); }
    public String toString() { return Double.toString(sum()); }
}
