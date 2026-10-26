package port.demo;
import java.nio.ByteBuffer;
public final class MemoryMappingProbe {
    public static ByteBuffer memAlloc(int count){throw new AssertionError("Memory mapping missing");}
    public static long memAddress(ByteBuffer buffer){throw new AssertionError("Memory mapping missing");}
    public static void memPutLong(long address,long value){throw new AssertionError("Memory mapping missing");}
    public static long memGetLong(long address){throw new AssertionError("Memory mapping missing");}
    public static void memFree(ByteBuffer buffer){throw new AssertionError("Memory mapping missing");}
}
