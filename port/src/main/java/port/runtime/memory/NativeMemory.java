package port.runtime.memory;
import java.nio.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Implemented LWJGL memory API subset; lengths are bytes unless the API names an element type. */
public final class NativeMemory {
    public static long nmemAlloc(long bytes){return NativeArena.allocateUninit(bytes,8);}
    public static long nmemAllocChecked(long bytes){return nmemAlloc(bytes);}
    public static long nmemCalloc(long count,long bytes){return NativeArena.allocate(Math.multiplyExact(count,bytes),8);}
    public static long nmemCallocChecked(long count,long bytes){return nmemCalloc(count,bytes);}
    public static long nmemRealloc(long address,long bytes){return NativeArena.reallocate(address,bytes);}
    public static long nmemReallocChecked(long address,long bytes){return NativeArena.reallocate(address,bytes==0?1:bytes);}
    public static long nmemAlignedAlloc(long alignment,long bytes){return NativeArena.allocateUninit(bytes,alignment);}
    public static long nmemAlignedAllocChecked(long alignment,long bytes){return nmemAlignedAlloc(alignment,bytes);}
    public static void nmemFree(long address){NativeArena.free(address);}
    public static void nmemAlignedFree(long address){nmemFree(address);}
    public static void memCopy(long source,long target,long bytes){NativeArena.copy(source,target,bytes);}
    // Round 59 (diag165.log): zombie.core.textures.PNGDecoder.decodeFrame and ImageData.<init>
    // call MemoryUtil.memCopy(ByteBuffer,ByteBuffer) while loading media/ui/Progress/*.png, and
    // MemoryCompatibility raised "Browser LWJGL memory operation unavailable" because only the
    // long/long/long overload existed here. LWJGL semantics (verified with javap -c on the pinned
    // org.lwjgl.system.MemoryUtil): Checks.check(target, source.remaining()), then
    // memcpy(memAddress(source), memAddress(target), source.remaining()) - the positions of both
    // buffers are NOT advanced, which is what the game relies on because it rewinds the
    // MipMapLevel buffer before copying.
    public static void memCopy(ByteBuffer source,ByteBuffer target){
        int bytes = source.remaining();
        if (bytes > target.remaining()) throw new BufferOverflowException();
        memCopy(NativeArena.address(source),NativeArena.address(target),bytes);
    }
    public static void memSet(long address,int value,long bytes){NativeArena.fill(address,bytes,(byte)value);}
    public static long memAddress(Buffer buffer){return NativeArena.address(buffer);}
    public static long memAddressSafe(Buffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(Buffer buffer){if(buffer!=null)NativeArena.free(NativeArena.address(buffer,0));}
    public static ByteBuffer memAlloc(int bytes){return NativeArena.buffer(nmemAlloc(bytes),bytes);}
    public static ByteBuffer memCalloc(int bytes){return NativeArena.buffer(nmemCalloc(1,bytes),bytes);}
    public static ByteBuffer memCalloc(int count,int bytes){return memCalloc(Math.multiplyExact(count,bytes));}
    public static ByteBuffer memByteBuffer(long address,int size){return NativeArena.buffer(address,size);}
    public static ByteBuffer memByteBufferSafe(long address,int size){return address==0?null:memByteBuffer(address,size);}
    public static ByteBuffer wrapBufferByte(long address,int size){return memByteBuffer(address,size);}
    public static ByteBuffer memRealloc(ByteBuffer buffer,int capacity){int pos=buffer==null?0:buffer.position();long p=nmemReallocChecked(buffer==null?0:NativeArena.address(buffer,0),capacity);return NativeArena.buffer(p,capacity).position(Math.min(pos,capacity));}
    public static ByteBuffer memSlice(ByteBuffer buffer){return memByteBuffer(memAddress(buffer),buffer.remaining());}
    public static ByteBuffer memSlice(ByteBuffer buffer,int offset,int length){Objects.checkFromIndexSize(offset,length,buffer.capacity());return memByteBuffer(NativeArena.address(buffer,offset),length);}
    public static ByteBuffer memDuplicate(ByteBuffer buffer){return memByteBuffer(NativeArena.address(buffer,0),buffer.capacity()).limit(buffer.limit()).position(buffer.position());}
    public static boolean memGetBoolean(long address){return NativeArena.getByte(address)!=0;}
    public static long memGetAddress(long address){return NativeArena.getLong(address);} public static long memGetCLong(long address){return NativeArena.getLong(address);}
    public static void memPutAddress(long address,long value){NativeArena.putLong(address,value);}public static void memPutCLong(long address,long value){NativeArena.putLong(address,value);}
    private static int terminated(long address,int maximum,int width){int n=0;while(n<=maximum-width){boolean zero=true;for(int i=0;i<width;i++)zero &= NativeArena.getByte(address+n+i)==0;if(zero)return n;n+=width;}return n;}
    public static int memLengthNT1(ByteBuffer buffer){return terminated(memAddress(buffer),buffer.remaining(),1);}
    public static int memLengthNT2(ByteBuffer buffer){return terminated(memAddress(buffer),buffer.remaining(),2);}
    public static long memAddress(ByteBuffer buffer){return NativeArena.address(buffer);}
    public static long memAddress(ByteBuffer buffer,int position){return NativeArena.address(buffer,position);}
    public static long memAddress0(ByteBuffer buffer){return NativeArena.address(buffer,0);}
    public static long memAddressSafe(ByteBuffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(ByteBuffer buffer){memFree((Buffer)buffer);}
    public static void memSet(ByteBuffer buffer,int value){memSet(memAddress(buffer),value,(long)buffer.remaining()*1);}
    public static byte memGetByte(long address){return NativeArena.getByte(address);}
    public static void memPutByte(long address,byte value){NativeArena.putByte(address,value);}
    public static void memCopy(byte[] data,long address){memCopy(data,address,0,data.length);}
    public static void memCopy(byte[] data,long address,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);NativeArena.putBytes(address,data,offset,length);}
    public static void memCopy(long address,byte[] data){memCopy(address,data,0,data.length);}
    public static void memCopy(long address,byte[] data,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);NativeArena.getBytes(address,data,offset,length);}
    public static long memAddress(ShortBuffer buffer){return NativeArena.address(buffer);}
    public static long memAddress(ShortBuffer buffer,int position){return NativeArena.address(buffer,position);}
    public static long memAddress0(ShortBuffer buffer){return NativeArena.address(buffer,0);}
    public static long memAddressSafe(ShortBuffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(ShortBuffer buffer){memFree((Buffer)buffer);}
    public static void memSet(ShortBuffer buffer,int value){memSet(memAddress(buffer),value,(long)buffer.remaining()*2);}
    public static short memGetShort(long address){return NativeArena.getShort(address);}
    public static void memPutShort(long address,short value){NativeArena.putShort(address,value);}
    public static void memCopy(short[] data,long address){memCopy(data,address,0,data.length);}
    public static void memCopy(short[] data,long address,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*2).asShortBuffer().put(data,offset,length);}
    public static void memCopy(long address,short[] data){memCopy(address,data,0,data.length);}
    public static void memCopy(long address,short[] data,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*2).asShortBuffer().get(data,offset,length);}
    public static ShortBuffer memShortBuffer(long address,int count){return NativeArena.registerView(address,NativeArena.buffer(address,Math.multiplyExact(count,2)).asShortBuffer(),2);}
    public static ShortBuffer memShortBufferSafe(long address,int count){return address==0?null:memShortBuffer(address,count);}
    public static ShortBuffer wrapBufferShort(long address,int count){return memShortBuffer(address,count);}
    public static ShortBuffer memAllocShort(int count){return memShortBuffer(nmemAlloc(Math.multiplyExact((long)count,2)),count);}
    public static ShortBuffer memCallocShort(int count){return memShortBuffer(nmemCalloc(count,2),count);}
    public static ShortBuffer memRealloc(ShortBuffer buffer,int count){int pos=buffer==null?0:buffer.position();long p=nmemReallocChecked(buffer==null?0:NativeArena.address(buffer,0),Math.multiplyExact((long)count,2));return memShortBuffer(p,count).position(Math.min(pos,count));}
    public static ShortBuffer memSlice(ShortBuffer buffer){return memShortBuffer(memAddress(buffer),buffer.remaining());}
    public static ShortBuffer memSlice(ShortBuffer buffer,int offset,int length){Objects.checkFromIndexSize(offset,length,buffer.capacity());return memShortBuffer(NativeArena.address(buffer,offset),length);}
    public static ShortBuffer memDuplicate(ShortBuffer buffer){return memShortBuffer(NativeArena.address(buffer,0),buffer.capacity()).limit(buffer.limit()).position(buffer.position());}
    public static long memAddress(CharBuffer buffer){return NativeArena.address(buffer);}
    public static long memAddress(CharBuffer buffer,int position){return NativeArena.address(buffer,position);}
    public static long memAddress0(CharBuffer buffer){return NativeArena.address(buffer,0);}
    public static long memAddressSafe(CharBuffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(CharBuffer buffer){memFree((Buffer)buffer);}
    public static void memSet(CharBuffer buffer,int value){memSet(memAddress(buffer),value,(long)buffer.remaining()*2);}
    public static char memGetChar(long address){return NativeArena.getChar(address);}
    public static void memPutChar(long address,char value){NativeArena.putChar(address,value);}
    public static void memCopy(char[] data,long address){memCopy(data,address,0,data.length);}
    public static void memCopy(char[] data,long address,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*2).asCharBuffer().put(data,offset,length);}
    public static void memCopy(long address,char[] data){memCopy(address,data,0,data.length);}
    public static void memCopy(long address,char[] data,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*2).asCharBuffer().get(data,offset,length);}
    public static CharBuffer memCharBuffer(long address,int count){return NativeArena.registerView(address,NativeArena.buffer(address,Math.multiplyExact(count,2)).asCharBuffer(),2);}
    public static CharBuffer memCharBufferSafe(long address,int count){return address==0?null:memCharBuffer(address,count);}
    public static CharBuffer wrapBufferChar(long address,int count){return memCharBuffer(address,count);}
    public static CharBuffer memAllocChar(int count){return memCharBuffer(nmemAlloc(Math.multiplyExact((long)count,2)),count);}
    public static CharBuffer memCallocChar(int count){return memCharBuffer(nmemCalloc(count,2),count);}
    public static CharBuffer memRealloc(CharBuffer buffer,int count){int pos=buffer==null?0:buffer.position();long p=nmemReallocChecked(buffer==null?0:NativeArena.address(buffer,0),Math.multiplyExact((long)count,2));return memCharBuffer(p,count).position(Math.min(pos,count));}
    public static CharBuffer memSlice(CharBuffer buffer){return memCharBuffer(memAddress(buffer),buffer.remaining());}
    public static CharBuffer memSlice(CharBuffer buffer,int offset,int length){Objects.checkFromIndexSize(offset,length,buffer.capacity());return memCharBuffer(NativeArena.address(buffer,offset),length);}
    public static CharBuffer memDuplicate(CharBuffer buffer){return memCharBuffer(NativeArena.address(buffer,0),buffer.capacity()).limit(buffer.limit()).position(buffer.position());}
    public static long memAddress(IntBuffer buffer){return NativeArena.address(buffer);}
    public static long memAddress(IntBuffer buffer,int position){return NativeArena.address(buffer,position);}
    public static long memAddress0(IntBuffer buffer){return NativeArena.address(buffer,0);}
    public static long memAddressSafe(IntBuffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(IntBuffer buffer){memFree((Buffer)buffer);}
    public static void memSet(IntBuffer buffer,int value){memSet(memAddress(buffer),value,(long)buffer.remaining()*4);}
    public static int memGetInt(long address){return NativeArena.getInt(address);}
    public static void memPutInt(long address,int value){NativeArena.putInt(address,value);}
    public static void memCopy(int[] data,long address){memCopy(data,address,0,data.length);}
    public static void memCopy(int[] data,long address,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*4).asIntBuffer().put(data,offset,length);}
    public static void memCopy(long address,int[] data){memCopy(address,data,0,data.length);}
    public static void memCopy(long address,int[] data,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*4).asIntBuffer().get(data,offset,length);}
    public static IntBuffer memIntBuffer(long address,int count){return NativeArena.registerView(address,NativeArena.buffer(address,Math.multiplyExact(count,4)).asIntBuffer(),4);}
    public static IntBuffer memIntBufferSafe(long address,int count){return address==0?null:memIntBuffer(address,count);}
    public static IntBuffer wrapBufferInt(long address,int count){return memIntBuffer(address,count);}
    public static IntBuffer memAllocInt(int count){return memIntBuffer(nmemAlloc(Math.multiplyExact((long)count,4)),count);}
    public static IntBuffer memCallocInt(int count){return memIntBuffer(nmemCalloc(count,4),count);}
    public static IntBuffer memRealloc(IntBuffer buffer,int count){int pos=buffer==null?0:buffer.position();long p=nmemReallocChecked(buffer==null?0:NativeArena.address(buffer,0),Math.multiplyExact((long)count,4));return memIntBuffer(p,count).position(Math.min(pos,count));}
    public static IntBuffer memSlice(IntBuffer buffer){return memIntBuffer(memAddress(buffer),buffer.remaining());}
    public static IntBuffer memSlice(IntBuffer buffer,int offset,int length){Objects.checkFromIndexSize(offset,length,buffer.capacity());return memIntBuffer(NativeArena.address(buffer,offset),length);}
    public static IntBuffer memDuplicate(IntBuffer buffer){return memIntBuffer(NativeArena.address(buffer,0),buffer.capacity()).limit(buffer.limit()).position(buffer.position());}
    public static long memAddress(LongBuffer buffer){return NativeArena.address(buffer);}
    public static long memAddress(LongBuffer buffer,int position){return NativeArena.address(buffer,position);}
    public static long memAddress0(LongBuffer buffer){return NativeArena.address(buffer,0);}
    public static long memAddressSafe(LongBuffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(LongBuffer buffer){memFree((Buffer)buffer);}
    public static void memSet(LongBuffer buffer,int value){memSet(memAddress(buffer),value,(long)buffer.remaining()*8);}
    public static long memGetLong(long address){return NativeArena.getLong(address);}
    public static void memPutLong(long address,long value){NativeArena.putLong(address,value);}
    public static void memCopy(long[] data,long address){memCopy(data,address,0,data.length);}
    public static void memCopy(long[] data,long address,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*8).asLongBuffer().put(data,offset,length);}
    public static void memCopy(long address,long[] data){memCopy(address,data,0,data.length);}
    public static void memCopy(long address,long[] data,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*8).asLongBuffer().get(data,offset,length);}
    public static LongBuffer memLongBuffer(long address,int count){return NativeArena.registerView(address,NativeArena.buffer(address,Math.multiplyExact(count,8)).asLongBuffer(),8);}
    public static LongBuffer memLongBufferSafe(long address,int count){return address==0?null:memLongBuffer(address,count);}
    public static LongBuffer wrapBufferLong(long address,int count){return memLongBuffer(address,count);}
    public static LongBuffer memAllocLong(int count){return memLongBuffer(nmemAlloc(Math.multiplyExact((long)count,8)),count);}
    public static LongBuffer memCallocLong(int count){return memLongBuffer(nmemCalloc(count,8),count);}
    public static LongBuffer memRealloc(LongBuffer buffer,int count){int pos=buffer==null?0:buffer.position();long p=nmemReallocChecked(buffer==null?0:NativeArena.address(buffer,0),Math.multiplyExact((long)count,8));return memLongBuffer(p,count).position(Math.min(pos,count));}
    public static LongBuffer memSlice(LongBuffer buffer){return memLongBuffer(memAddress(buffer),buffer.remaining());}
    public static LongBuffer memSlice(LongBuffer buffer,int offset,int length){Objects.checkFromIndexSize(offset,length,buffer.capacity());return memLongBuffer(NativeArena.address(buffer,offset),length);}
    public static LongBuffer memDuplicate(LongBuffer buffer){return memLongBuffer(NativeArena.address(buffer,0),buffer.capacity()).limit(buffer.limit()).position(buffer.position());}
    public static long memAddress(FloatBuffer buffer){return NativeArena.address(buffer);}
    public static long memAddress(FloatBuffer buffer,int position){return NativeArena.address(buffer,position);}
    public static long memAddress0(FloatBuffer buffer){return NativeArena.address(buffer,0);}
    public static long memAddressSafe(FloatBuffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(FloatBuffer buffer){memFree((Buffer)buffer);}
    public static void memSet(FloatBuffer buffer,int value){memSet(memAddress(buffer),value,(long)buffer.remaining()*4);}
    public static float memGetFloat(long address){return NativeArena.getFloat(address);}
    public static void memPutFloat(long address,float value){NativeArena.putFloat(address,value);}
    public static void memCopy(float[] data,long address){memCopy(data,address,0,data.length);}
    public static void memCopy(float[] data,long address,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*4).asFloatBuffer().put(data,offset,length);}
    public static void memCopy(long address,float[] data){memCopy(address,data,0,data.length);}
    public static void memCopy(long address,float[] data,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*4).asFloatBuffer().get(data,offset,length);}
    public static FloatBuffer memFloatBuffer(long address,int count){return NativeArena.registerView(address,NativeArena.buffer(address,Math.multiplyExact(count,4)).asFloatBuffer(),4);}
    public static FloatBuffer memFloatBufferSafe(long address,int count){return address==0?null:memFloatBuffer(address,count);}
    public static FloatBuffer wrapBufferFloat(long address,int count){return memFloatBuffer(address,count);}
    public static FloatBuffer memAllocFloat(int count){return memFloatBuffer(nmemAlloc(Math.multiplyExact((long)count,4)),count);}
    public static FloatBuffer memCallocFloat(int count){return memFloatBuffer(nmemCalloc(count,4),count);}
    public static FloatBuffer memRealloc(FloatBuffer buffer,int count){int pos=buffer==null?0:buffer.position();long p=nmemReallocChecked(buffer==null?0:NativeArena.address(buffer,0),Math.multiplyExact((long)count,4));return memFloatBuffer(p,count).position(Math.min(pos,count));}
    public static FloatBuffer memSlice(FloatBuffer buffer){return memFloatBuffer(memAddress(buffer),buffer.remaining());}
    public static FloatBuffer memSlice(FloatBuffer buffer,int offset,int length){Objects.checkFromIndexSize(offset,length,buffer.capacity());return memFloatBuffer(NativeArena.address(buffer,offset),length);}
    public static FloatBuffer memDuplicate(FloatBuffer buffer){return memFloatBuffer(NativeArena.address(buffer,0),buffer.capacity()).limit(buffer.limit()).position(buffer.position());}
    public static long memAddress(DoubleBuffer buffer){return NativeArena.address(buffer);}
    public static long memAddress(DoubleBuffer buffer,int position){return NativeArena.address(buffer,position);}
    public static long memAddress0(DoubleBuffer buffer){return NativeArena.address(buffer,0);}
    public static long memAddressSafe(DoubleBuffer buffer){return buffer==null?0:memAddress(buffer);}
    public static void memFree(DoubleBuffer buffer){memFree((Buffer)buffer);}
    public static void memSet(DoubleBuffer buffer,int value){memSet(memAddress(buffer),value,(long)buffer.remaining()*8);}
    public static double memGetDouble(long address){return NativeArena.getDouble(address);}
    public static void memPutDouble(long address,double value){NativeArena.putDouble(address,value);}
    public static void memCopy(double[] data,long address){memCopy(data,address,0,data.length);}
    public static void memCopy(double[] data,long address,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*8).asDoubleBuffer().put(data,offset,length);}
    public static void memCopy(long address,double[] data){memCopy(address,data,0,data.length);}
    public static void memCopy(long address,double[] data,int offset,int length){Objects.checkFromIndexSize(offset,length,data.length);if(length>0)NativeArena.raw(address,(long)length*8).asDoubleBuffer().get(data,offset,length);}
    public static DoubleBuffer memDoubleBuffer(long address,int count){return NativeArena.registerView(address,NativeArena.buffer(address,Math.multiplyExact(count,8)).asDoubleBuffer(),8);}
    public static DoubleBuffer memDoubleBufferSafe(long address,int count){return address==0?null:memDoubleBuffer(address,count);}
    public static DoubleBuffer wrapBufferDouble(long address,int count){return memDoubleBuffer(address,count);}
    public static DoubleBuffer memAllocDouble(int count){return memDoubleBuffer(nmemAlloc(Math.multiplyExact((long)count,8)),count);}
    public static DoubleBuffer memCallocDouble(int count){return memDoubleBuffer(nmemCalloc(count,8),count);}
    public static DoubleBuffer memRealloc(DoubleBuffer buffer,int count){int pos=buffer==null?0:buffer.position();long p=nmemReallocChecked(buffer==null?0:NativeArena.address(buffer,0),Math.multiplyExact((long)count,8));return memDoubleBuffer(p,count).position(Math.min(pos,count));}
    public static DoubleBuffer memSlice(DoubleBuffer buffer){return memDoubleBuffer(memAddress(buffer),buffer.remaining());}
    public static DoubleBuffer memSlice(DoubleBuffer buffer,int offset,int length){Objects.checkFromIndexSize(offset,length,buffer.capacity());return memDoubleBuffer(NativeArena.address(buffer,offset),length);}
    public static DoubleBuffer memDuplicate(DoubleBuffer buffer){return memDoubleBuffer(NativeArena.address(buffer,0),buffer.capacity()).limit(buffer.limit()).position(buffer.position());}
    private static byte[] encodeASCII(CharSequence text){Objects.requireNonNull(text);byte[] bytes=new byte[text.length()];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)text.charAt(i);return bytes;}
    /**
     * Same layout contract as {@code MemoryUtil.encodeASCIIUnsafe}: the bytes are written
     * directly at the arena address, optionally NUL terminated, and the written length is
     * returned. {@code MemoryStack.ASCII} (and therefore {@code FunctionProvider
     * .getFunctionAddress(CharSequence)}) calls this, so the browser provider can resolve names.
     */
    public static int encodeASCIIUnsafe(CharSequence text, boolean terminate, long address){
        Objects.requireNonNull(text);
        for(int i=0;i<text.length();i++)NativeArena.putByte(address+(long)i,(byte)text.charAt(i));
        if(terminate)NativeArena.putByte(address+text.length(),(byte)0);
        return text.length()+(terminate?1:0);
    }
    public static ByteBuffer memASCII(CharSequence text){return memASCII(text,true);}
    public static ByteBuffer memASCII(CharSequence text,boolean terminate){byte[] bytes=encodeASCII(text);var out=memAlloc(bytes.length+(terminate?1:0));out.put(bytes).clear();return out;}
    public static ByteBuffer memASCIISafe(CharSequence text){return text==null?null:memASCII(text);}
    public static ByteBuffer memASCIISafe(CharSequence text,boolean terminate){return text==null?null:memASCII(text,terminate);}
    public static int memLengthASCII(CharSequence text,boolean terminate){return encodeASCII(text).length+(terminate?1:0);}
    public static int memASCII(CharSequence text,boolean terminate,ByteBuffer target){return memASCII(text,terminate,target,target.position());}
    public static int memASCII(CharSequence text,boolean terminate,ByteBuffer target,int offset){byte[] bytes=encodeASCII(text);int n=bytes.length+(terminate?1:0);Objects.checkFromIndexSize(offset,n,target.limit());for(int i=0;i<n;i++)target.put(offset+i,i<bytes.length?bytes[i]:0);return n;}
    public static String memASCII(long address){return memASCII(address,terminated(address,Integer.MAX_VALUE,1)/1);}
    public static String memASCII(long address,int length){byte[] data=new byte[Math.multiplyExact(length,1)];NativeArena.getBytes(address,data,0,data.length);return new String(data,StandardCharsets.ISO_8859_1);}
    public static String memASCIISafe(long address){return address==0?null:memASCII(address);}
    public static String memASCIISafe(long address,int length){return address==0?null:memASCII(address,length);}
    public static String memASCII(ByteBuffer data){return memASCII(data,data.remaining()/1,data.position());}
    public static String memASCII(ByteBuffer data,int length){return memASCII(data,length,data.position());}
    public static String memASCII(ByteBuffer bytes,int length,int offset){Objects.checkFromIndexSize(offset,Math.multiplyExact(length,1),bytes.limit());byte[] data=new byte[length*1];for(int i=0;i<data.length;i++)data[i]=bytes.get(offset+i);return new String(data,StandardCharsets.ISO_8859_1);}
    public static String memASCIISafe(ByteBuffer data){return data==null?null:memASCII(data);}
    private static byte[] encodeUTF8(CharSequence text){Objects.requireNonNull(text);return text.toString().getBytes(StandardCharsets.UTF_8);}
    public static ByteBuffer memUTF8(CharSequence text){return memUTF8(text,true);}
    public static ByteBuffer memUTF8(CharSequence text,boolean terminate){byte[] bytes=encodeUTF8(text);var out=memAlloc(bytes.length+(terminate?1:0));out.put(bytes).clear();return out;}
    public static ByteBuffer memUTF8Safe(CharSequence text){return text==null?null:memUTF8(text);}
    public static ByteBuffer memUTF8Safe(CharSequence text,boolean terminate){return text==null?null:memUTF8(text,terminate);}
    public static int memLengthUTF8(CharSequence text,boolean terminate){return encodeUTF8(text).length+(terminate?1:0);}
    public static int memUTF8(CharSequence text,boolean terminate,ByteBuffer target){return memUTF8(text,terminate,target,target.position());}
    public static int memUTF8(CharSequence text,boolean terminate,ByteBuffer target,int offset){byte[] bytes=encodeUTF8(text);int n=bytes.length+(terminate?1:0);Objects.checkFromIndexSize(offset,n,target.limit());for(int i=0;i<n;i++)target.put(offset+i,i<bytes.length?bytes[i]:0);return n;}
    public static String memUTF8(long address){return memUTF8(address,terminated(address,Integer.MAX_VALUE,1)/1);}
    public static String memUTF8(long address,int length){byte[] data=new byte[Math.multiplyExact(length,1)];NativeArena.getBytes(address,data,0,data.length);return new String(data,StandardCharsets.UTF_8);}
    public static String memUTF8Safe(long address){return address==0?null:memUTF8(address);}
    public static String memUTF8Safe(long address,int length){return address==0?null:memUTF8(address,length);}
    public static String memUTF8(ByteBuffer data){return memUTF8(data,data.remaining()/1,data.position());}
    public static String memUTF8(ByteBuffer data,int length){return memUTF8(data,length,data.position());}
    public static String memUTF8(ByteBuffer bytes,int length,int offset){Objects.checkFromIndexSize(offset,Math.multiplyExact(length,1),bytes.limit());byte[] data=new byte[length*1];for(int i=0;i<data.length;i++)data[i]=bytes.get(offset+i);return new String(data,StandardCharsets.UTF_8);}
    public static String memUTF8Safe(ByteBuffer data){return data==null?null:memUTF8(data);}
    private static byte[] encodeUTF16(CharSequence text){Objects.requireNonNull(text);byte[] bytes=new byte[Math.multiplyExact(text.length(),2)];for(int i=0;i<text.length();i++){bytes[2*i]=(byte)text.charAt(i);bytes[2*i+1]=(byte)(text.charAt(i)>>>8);}return bytes;}
    public static ByteBuffer memUTF16(CharSequence text){return memUTF16(text,true);}
    public static ByteBuffer memUTF16(CharSequence text,boolean terminate){byte[] bytes=encodeUTF16(text);var out=memAlloc(bytes.length+(terminate?2:0));out.put(bytes).clear();return out;}
    public static ByteBuffer memUTF16Safe(CharSequence text){return text==null?null:memUTF16(text);}
    public static ByteBuffer memUTF16Safe(CharSequence text,boolean terminate){return text==null?null:memUTF16(text,terminate);}
    public static int memLengthUTF16(CharSequence text,boolean terminate){return encodeUTF16(text).length+(terminate?2:0);}
    public static int memUTF16(CharSequence text,boolean terminate,ByteBuffer target){return memUTF16(text,terminate,target,target.position());}
    public static int memUTF16(CharSequence text,boolean terminate,ByteBuffer target,int offset){byte[] bytes=encodeUTF16(text);int n=bytes.length+(terminate?2:0);Objects.checkFromIndexSize(offset,n,target.limit());for(int i=0;i<n;i++)target.put(offset+i,i<bytes.length?bytes[i]:0);return n;}
    public static String memUTF16(long address){return memUTF16(address,terminated(address,Integer.MAX_VALUE,2)/2);}
    public static String memUTF16(long address,int length){byte[] data=new byte[Math.multiplyExact(length,2)];NativeArena.getBytes(address,data,0,data.length);return new String(data,StandardCharsets.UTF_16LE);}
    public static String memUTF16Safe(long address){return address==0?null:memUTF16(address);}
    public static String memUTF16Safe(long address,int length){return address==0?null:memUTF16(address,length);}
    public static String memUTF16(ByteBuffer data){return memUTF16(data,data.remaining()/2,data.position());}
    public static String memUTF16(ByteBuffer data,int length){return memUTF16(data,length,data.position());}
    public static String memUTF16(ByteBuffer bytes,int length,int offset){Objects.checkFromIndexSize(offset,Math.multiplyExact(length,2),bytes.limit());byte[] data=new byte[length*2];for(int i=0;i<data.length;i++)data[i]=bytes.get(offset+i);return new String(data,StandardCharsets.UTF_16LE);}
    public static String memUTF16Safe(ByteBuffer data){return data==null?null:memUTF16(data);}
    public static ByteBuffer memByteBufferNT1(long address){return memByteBufferNT1(address,Integer.MAX_VALUE);}
    public static ByteBuffer memByteBufferNT1(long address,int maximum){return memByteBuffer(address,terminated(address,maximum,1));}
    public static ByteBuffer memByteBufferNT1Safe(long address){return address==0?null:memByteBufferNT1(address);}
    public static ByteBuffer memByteBufferNT1Safe(long address,int maximum){return address==0?null:memByteBufferNT1(address,maximum);}
    public static ByteBuffer memByteBufferNT2(long address){return memByteBufferNT2(address,Integer.MAX_VALUE);}
    public static ByteBuffer memByteBufferNT2(long address,int maximum){return memByteBuffer(address,terminated(address,maximum,2));}
    public static ByteBuffer memByteBufferNT2Safe(long address){return address==0?null:memByteBufferNT2(address);}
    public static ByteBuffer memByteBufferNT2Safe(long address,int maximum){return address==0?null:memByteBufferNT2(address,maximum);}
}
