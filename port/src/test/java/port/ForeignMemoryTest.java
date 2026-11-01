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
    // Round 80 (front Q): MemoryStack.nUTF8 (stbi_write_png's file name) = memLengthUTF8 + encodeUTF8Unsafe at the same
    // address; expected bytes follow LWJGL's encoder (lone low surrogate = 3 bytes ED B0 80, not '?').
    @Test void lwjglUnsafeEncodersWriteAtTheAddress(){
        String text="a\u00f1\u20ac\ud83d\ude00\udc00";
        byte[] expected={0x61,(byte)0xC3,(byte)0xB1,(byte)0xE2,(byte)0x82,(byte)0xAC,(byte)0xF0,(byte)0x9F,(byte)0x98,(byte)0x80,(byte)0xED,(byte)0xB0,(byte)0x80,0};
        assertEquals(14,NativeMemory.memLengthUTF8(text,true));assertEquals(13,NativeMemory.memLengthUTF8(text,false));
        long address=NativeMemory.nmemAlloc(32);
        try{
            for(int i=0;i<32;i++)NativeMemory.memPutByte(address+i,(byte)0x55);
            assertEquals(14,NativeMemory.encodeUTF8Unsafe(text,true,address));
            for(int i=0;i<expected.length;i++)assertEquals(expected[i],NativeMemory.memGetByte(address+i),"utf8 byte "+i);
            assertEquals((byte)0x55,NativeMemory.memGetByte(address+14),"no write past the NUL");
            assertEquals(13,NativeMemory.encodeUTF8Unsafe("thumb.png\u00f1!x",false,address+16));
            assertEquals("thumb.png\u00f1!x",NativeMemory.memUTF8(address+16,13));
            var path=NativeMemory.memUTF8("/Zomboid/Saves/Apocalypse/x/thumb.png",true);
            assertEquals(NativeMemory.memLengthUTF8("/Zomboid/Saves/Apocalypse/x/thumb.png",true),path.capacity());NativeMemory.memFree(path);
            assertEquals(0,NativeMemory.encodeUTF8Unsafe("",false,address));assertEquals(1,NativeMemory.encodeUTF8Unsafe("",true,address));assertEquals(0,NativeMemory.memGetByte(address));
            assertThrows(IndexOutOfBoundsException.class,()->NativeMemory.encodeUTF8Unsafe("x\ud83d",true,address));
            assertEquals(8,NativeMemory.encodeUTF16Unsafe("a\u20ac\ud83d",true,address));
            assertEquals(0x61,NativeMemory.memGetByte(address));assertEquals(0,NativeMemory.memGetByte(address+1));
            assertEquals((byte)0xAC,NativeMemory.memGetByte(address+2));assertEquals(0x20,NativeMemory.memGetByte(address+3));
            assertEquals((byte)0x3D,NativeMemory.memGetByte(address+4));assertEquals((byte)0xD8,NativeMemory.memGetByte(address+5));
            assertEquals(0,NativeMemory.memGetByte(address+6));assertEquals(0,NativeMemory.memGetByte(address+7));
            assertEquals(6,NativeMemory.encodeUTF16Unsafe("a\u20ac\ud83d",false,address));
            assertEquals(4,NativeMemory.encodeASCIIUnsafe("png",true,address));
        }finally{NativeMemory.nmemFree(address);}
    }
    @Test void unsafeArraysAndNativeArena(){
        var unsafe=port.compat.sun.misc.Unsafe.getUnsafe();int[] words={0x12345678};assertEquals((byte)0x78,unsafe.getByte(words,0));unsafe.putByte(words,3,(byte)0x80);assertEquals(0x80345678,words[0]);
        long address=unsafe.allocateMemory(8);try{unsafe.putDouble(address,Math.PI);assertEquals(Double.doubleToRawLongBits(Math.PI),unsafe.getLong(address));}finally{unsafe.freeMemory(address);}
    }
}
