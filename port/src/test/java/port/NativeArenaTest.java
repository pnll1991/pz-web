package port;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import port.runtime.memory.NativeArena;
class NativeArenaTest {
    @org.junit.jupiter.api.Test void zeroCapacityReallocationHasAFreeableAddress(){
        var original=port.runtime.memory.NativeMemory.memAlloc(8);var empty=port.runtime.memory.NativeMemory.memRealloc(original,0);
        org.junit.jupiter.api.Assertions.assertEquals(0,empty.capacity());org.junit.jupiter.api.Assertions.assertNotEquals(0,port.runtime.memory.NativeMemory.memAddress(empty));port.runtime.memory.NativeMemory.memFree(empty);
    }

    @Test void aliasesBoundsAndLifetime() {
        long base=NativeArena.allocate(32,64); assertEquals(0,base%64);
        try {
            NativeArena.putLong(base,0x8877665544332211L);
            var bytes=NativeArena.buffer(base,16);
            assertEquals((byte)0x88,bytes.get(7)); bytes.put(0,(byte)255);
            assertEquals(0x88776655443322ffL,NativeArena.getLong(base));
            bytes.position(3); assertEquals(base+3,NativeArena.address(bytes));
            NativeArena.copy(base,base+2,8); assertEquals((byte)255,NativeArena.getByte(base+2));
            assertThrows(IllegalStateException.class,()->NativeArena.getLong(base+25));
            assertThrows(IllegalArgumentException.class,()->NativeArena.free(base+1));
        } finally { NativeArena.free(base); }
        assertThrows(IllegalStateException.class,()->NativeArena.getByte(base));
        assertThrows(IllegalArgumentException.class,()->NativeArena.free(base));
    }
    @Test void reallocationAndDirectBuffers() {
        long base=NativeArena.allocate(4,1); NativeArena.putInt(base,0x89abcdef);
        long moved=NativeArena.reallocate(base,16);
        assertFalse(NativeArena.live(base,1)); assertEquals(0x89abcdef,NativeArena.getInt(moved)); NativeArena.free(moved);
        var bytes=ByteBuffer.allocateDirect(8); long p=NativeArena.address(bytes);
        NativeArena.putByte(p,(byte)7); assertEquals(7,bytes.get(0)); NativeArena.free(p);
        assertThrows(IllegalArgumentException.class,()->NativeArena.allocate(-1,8));
        assertThrows(IllegalArgumentException.class,()->NativeArena.allocate(1,3));
    }
}
