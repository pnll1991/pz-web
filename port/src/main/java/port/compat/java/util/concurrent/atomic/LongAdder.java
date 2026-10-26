package port.compat.java.util.concurrent.atomic;
/** No contention striping is needed for the single browser Java executor. */
public class LongAdder extends Number {
    private long value;
    public synchronized void add(long delta) { value+=delta; }
    public void increment() { add(1); } public void decrement() { add(-1); }
    public synchronized long sum() { return value; }
    public synchronized void reset() { value=0; }
    public synchronized long sumThenReset() { long previous=value;value=0;return previous; }
    public long longValue() { return sum(); } public int intValue() { return (int)sum(); }
    public float floatValue() { return sum(); } public double doubleValue() { return sum(); }
    public String toString() { return Long.toString(sum()); }
}
