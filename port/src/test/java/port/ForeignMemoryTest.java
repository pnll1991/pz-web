package port;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import port.compat.java.lang.foreign.*;
import port.runtime.memory.*;
class ForeignMemoryTest {
    @Test void scopedMemoryAlignmentAndClose()throws Exception{
        MemorySegment segment;
        try(var arena=Arena.ofConfined()){
            segment=arena.allocate(ValueLayout.JAVA_LONG,2);segment.set(ValueLayout.JAVA_LONG,0,Long.MIN_VALUE);assertEquals(Long.MIN_VALUE,segment.get(ValueLayout.JAVA_LONG,0));
            assertThrows(IllegalArgumentException.class,()->segment.get(ValueLayout.JAVA_INT,1));assertThrows(IndexOutOfBoundsException.class,()->segment.asSlice(17));
            Throwable[] error={null};Thread other=new Thread(()->{try{segment.get(ValueLayout.JAVA_LONG,0);}catch(Throwable e){error[0]=e;}});other.start();other.join();assertInstanceOf(port.compat.java.lang.WrongThreadException.class,error[0]);
        }
        assertThrows(IllegalStateException.class,()->segment.get(ValueLayout.JAVA_LONG,0));
    }
    @Test void lwjglBuffersStringsAndRetiredViews(){
        var bytes=NativeMemory.memUTF8("ñ😀",true);long address=NativeMemory.memAddress(bytes);assertEquals("ñ😀",NativeMemory.memUTF8(address));assertEquals(7,bytes.capacity());NativeMemory.memFree(bytes);assertThrows(IllegalStateException.class,()->NativeMemory.memAddress(bytes));
        var ints=NativeMemory.memAllocInt(4);try{ints.put(1,0x12345678);assertEquals(0x12345678,NativeMemory.memGetInt(NativeMemory.memAddress(ints,1)));NativeMemory.memPutInt(NativeMemory.memAddress(ints,2),-42);assertEquals(-42,ints.get(2));}finally{NativeMemory.memFree(ints);}
    }
    @Test void unsafeArraysAndNativeArena(){
        var unsafe=port.compat.sun.misc.Unsafe.getUnsafe();int[] words={0x12345678};assertEquals((byte)0x78,unsafe.getByte(words,0));unsafe.putByte(words,3,(byte)0x80);assertEquals(0x80345678,words[0]);
        long address=unsafe.allocateMemory(8);try{unsafe.putDouble(address,Math.PI);assertEquals(Double.doubleToRawLongBits(Math.PI),unsafe.getLong(address));}finally{unsafe.freeMemory(address);}
    }
}
