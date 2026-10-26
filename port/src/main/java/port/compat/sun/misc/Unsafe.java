package port.compat.sun.misc;
import java.lang.reflect.*;
import java.util.*;
import port.runtime.memory.NativeArena;
/** Virtual arena addresses and reflection field tokens; never JVM object-layout offsets. */
public class Unsafe {
    private static final Unsafe INSTANCE=new Unsafe();
    private static final Map<Long,Field> fields=new HashMap<>();private static long nextField=-1;
    public static final int ARRAY_BYTE_BASE_OFFSET=0,ARRAY_SHORT_BASE_OFFSET=0,ARRAY_INT_BASE_OFFSET=0,ARRAY_LONG_BASE_OFFSET=0,ARRAY_FLOAT_BASE_OFFSET=0,ARRAY_DOUBLE_BASE_OFFSET=0;
    public static Unsafe getUnsafe(){return INSTANCE;}
    public int addressSize(){return 8;} public int pageSize(){return 65536;}
    public int arrayBaseOffset(Class<?> type){if(!type.isArray())throw new IllegalArgumentException();return 0;}
    public int arrayIndexScale(Class<?> type){Class<?> c=type.getComponentType();if(c==byte.class||c==boolean.class)return 1;if(c==short.class||c==char.class)return 2;if(c==int.class||c==float.class)return 4;if(c==long.class||c==double.class)return 8;throw new UnsupportedOperationException("Unsafe reference array layout");}
    public synchronized long objectFieldOffset(Field field){if(Modifier.isStatic(field.getModifiers()))throw new IllegalArgumentException();return token(field);}
    public long objectFieldOffset(Class<?> cls,String name){try{return objectFieldOffset(cls.getDeclaredField(name));}catch(NoSuchFieldException e){throw new IllegalArgumentException(e);}}
    private static synchronized long token(Field field){for(var entry:fields.entrySet())if(entry.getValue().equals(field))return entry.getKey();field.setAccessible(true);long id=nextField--;fields.put(id,field);return id;}
    public long staticFieldOffset(Field field){if(!Modifier.isStatic(field.getModifiers()))throw new IllegalArgumentException();return token(field);}
    public Object staticFieldBase(Field field){return field.getDeclaringClass();}
    private Object fieldGet(Object target,long offset){Field field=fields.get(offset);if(field==null)throw new UnsupportedOperationException("Unsafe unknown object field token: "+offset);try{return field.get(Modifier.isStatic(field.getModifiers())?null:target);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    private void fieldPut(Object target,long offset,Object value){Field field=fields.get(offset);if(field==null)throw new UnsupportedOperationException("Unsafe unknown object field token: "+offset);if(Modifier.isFinal(field.getModifiers()))throw new UnsupportedOperationException("Unsafe write to final field: "+field.getName());try{field.set(Modifier.isStatic(field.getModifiers())?null:target,value);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    public Object allocateInstance(Class<?> type){throw new UnsupportedOperationException("Unsafe.allocateInstance: uninitialized GC objects are not supported: "+type.getName());}
    public long allocateMemory(long bytes){return NativeArena.allocate(bytes,8);}
    public void freeMemory(long address){NativeArena.free(address);}
    public long reallocateMemory(long address,long bytes){return NativeArena.reallocate(address,bytes);}
    public long getAddress(long address){return NativeArena.getLong(address);}public void putAddress(long address,long value){NativeArena.putLong(address,value);}
    private byte arrayByte(Object array,long offset){int stride=arrayIndexScale(array.getClass());long size=(long)Array.getLength(array)*stride;if(offset<0||offset>=size)throw new IndexOutOfBoundsException();int index=(int)(offset/stride),shift=(int)(offset%stride)*8;long bits;
        if(array instanceof byte[] a)bits=a[index];else if(array instanceof boolean[] a)bits=a[index]?1:0;else if(array instanceof short[] a)bits=a[index];else if(array instanceof char[] a)bits=a[index];else if(array instanceof int[] a)bits=a[index];else if(array instanceof long[] a)bits=a[index];else if(array instanceof float[] a)bits=Float.floatToRawIntBits(a[index]);else if(array instanceof double[] a)bits=Double.doubleToRawLongBits(a[index]);else throw new IllegalArgumentException();return (byte)(bits>>>shift);
    }
    private void arrayByte(Object array,long offset,byte value){int stride=arrayIndexScale(array.getClass());long size=(long)Array.getLength(array)*stride;if(offset<0||offset>=size)throw new IndexOutOfBoundsException();int index=(int)(offset/stride),shift=(int)(offset%stride)*8;long bits=0;for(int b=0;b<stride;b++)bits|=(arrayByte(array,(long)index*stride+b)&255L)<<(8*b);bits=(bits&~(255L<<shift))|((value&255L)<<shift);
        if(array instanceof byte[] a)a[index]=(byte)bits;else if(array instanceof boolean[] a)a[index]=bits!=0;else if(array instanceof short[] a)a[index]=(short)bits;else if(array instanceof char[] a)a[index]=(char)bits;else if(array instanceof int[] a)a[index]=(int)bits;else if(array instanceof long[] a)a[index]=bits;else if(array instanceof float[] a)a[index]=Float.intBitsToFloat((int)bits);else if(array instanceof double[] a)a[index]=Double.longBitsToDouble(bits);else throw new IllegalArgumentException();
    }
    private long rawGet(Object object,long offset,int bytes){if(object==null){long value=0;for(int i=0;i<bytes;i++)value|=(NativeArena.getByte(offset+i)&255L)<<(8*i);return value;}long value=0;for(int i=0;i<bytes;i++)value|=(arrayByte(object,offset+i)&255L)<<(8*i);return value;}
    private void rawPut(Object object,long offset,int bytes,long value){for(int i=0;i<bytes;i++){if(object==null)NativeArena.putByte(offset+i,(byte)(value>>>(8*i)));else arrayByte(object,offset+i,(byte)(value>>>(8*i)));}}
    public Object getObject(Object object,long offset){return fieldGet(object,offset);}public void putObject(Object object,long offset,Object value){fieldPut(object,offset,value);}
    public boolean getBoolean(Object object,long offset){return offset<0?(Boolean)fieldGet(object,offset):getByte(object,offset)!=0;}
    public void putBoolean(Object object,long offset,boolean value){if(offset<0)fieldPut(object,offset,value);else putByte(object,offset,(byte)(value?1:0));}
    public synchronized boolean compareAndSwapObject(Object object,long offset,Object expected,Object value){if(getObject(object,offset)!=expected)return false;putObject(object,offset,value);return true;}
    public synchronized boolean compareAndSwapInt(Object object,long offset,int expected,int value){if(getInt(object,offset)!=expected)return false;putInt(object,offset,value);return true;}
    public synchronized boolean compareAndSwapLong(Object object,long offset,long expected,long value){if(getLong(object,offset)!=expected)return false;putLong(object,offset,value);return true;}
    public void copyMemory(long source,long target,long length){NativeArena.copy(source,target,length);}
    public void copyMemory(Object source,long from,Object target,long to,long count){if(count<0||count>Integer.MAX_VALUE)throw new IllegalArgumentException();byte[] data=new byte[(int)count];for(int i=0;i<data.length;i++)data[i]=getByte(source,from+i);for(int i=0;i<data.length;i++)putByte(target,to+i,data[i]);}
    public void setMemory(long target,long bytes,byte value){NativeArena.fill(target,bytes,value);}
    public byte getByte(long address){return NativeArena.getByte(address);}
    public void putByte(long address,byte value){NativeArena.putByte(address,value);}
    public byte getByte(Object object,long offset){return offset<0?(Byte)fieldGet(object,offset):(byte)rawGet(object,offset,1);}
    public void putByte(Object object,long offset,byte value){if(offset<0)fieldPut(object,offset,value);else rawPut(object,offset,1,value);}
    public synchronized byte getByteVolatile(Object object,long offset){return getByte(object,offset);}
    public synchronized void putByteVolatile(Object object,long offset,byte value){putByte(object,offset,value);}
    public short getShort(long address){return NativeArena.getShort(address);}
    public void putShort(long address,short value){NativeArena.putShort(address,value);}
    public short getShort(Object object,long offset){return offset<0?(Short)fieldGet(object,offset):(short)rawGet(object,offset,2);}
    public void putShort(Object object,long offset,short value){if(offset<0)fieldPut(object,offset,value);else rawPut(object,offset,2,value);}
    public synchronized short getShortVolatile(Object object,long offset){return getShort(object,offset);}
    public synchronized void putShortVolatile(Object object,long offset,short value){putShort(object,offset,value);}
    public char getChar(long address){return NativeArena.getChar(address);}
    public void putChar(long address,char value){NativeArena.putChar(address,value);}
    public char getChar(Object object,long offset){return offset<0?(Character)fieldGet(object,offset):(char)rawGet(object,offset,2);}
    public void putChar(Object object,long offset,char value){if(offset<0)fieldPut(object,offset,value);else rawPut(object,offset,2,value);}
    public synchronized char getCharVolatile(Object object,long offset){return getChar(object,offset);}
    public synchronized void putCharVolatile(Object object,long offset,char value){putChar(object,offset,value);}
    public int getInt(long address){return NativeArena.getInt(address);}
    public void putInt(long address,int value){NativeArena.putInt(address,value);}
    public int getInt(Object object,long offset){return offset<0?(Integer)fieldGet(object,offset):(int)rawGet(object,offset,4);}
    public void putInt(Object object,long offset,int value){if(offset<0)fieldPut(object,offset,value);else rawPut(object,offset,4,value);}
    public synchronized int getIntVolatile(Object object,long offset){return getInt(object,offset);}
    public synchronized void putIntVolatile(Object object,long offset,int value){putInt(object,offset,value);}
    public long getLong(long address){return NativeArena.getLong(address);}
    public void putLong(long address,long value){NativeArena.putLong(address,value);}
    public long getLong(Object object,long offset){return offset<0?(Long)fieldGet(object,offset):(long)rawGet(object,offset,8);}
    public void putLong(Object object,long offset,long value){if(offset<0)fieldPut(object,offset,value);else rawPut(object,offset,8,value);}
    public synchronized long getLongVolatile(Object object,long offset){return getLong(object,offset);}
    public synchronized void putLongVolatile(Object object,long offset,long value){putLong(object,offset,value);}
    public float getFloat(long address){return NativeArena.getFloat(address);}
    public void putFloat(long address,float value){NativeArena.putFloat(address,value);}
    public float getFloat(Object object,long offset){return offset<0?(Float)fieldGet(object,offset):Float.intBitsToFloat((int)rawGet(object,offset,4));}
    public void putFloat(Object object,long offset,float value){if(offset<0)fieldPut(object,offset,value);else rawPut(object,offset,4,Float.floatToRawIntBits(value));}
    public synchronized float getFloatVolatile(Object object,long offset){return getFloat(object,offset);}
    public synchronized void putFloatVolatile(Object object,long offset,float value){putFloat(object,offset,value);}
    public double getDouble(long address){return NativeArena.getDouble(address);}
    public void putDouble(long address,double value){NativeArena.putDouble(address,value);}
    public double getDouble(Object object,long offset){return offset<0?(Double)fieldGet(object,offset):Double.longBitsToDouble(rawGet(object,offset,8));}
    public void putDouble(Object object,long offset,double value){if(offset<0)fieldPut(object,offset,value);else rawPut(object,offset,8,Double.doubleToRawLongBits(value));}
    public synchronized double getDoubleVolatile(Object object,long offset){return getDouble(object,offset);}
    public synchronized void putDoubleVolatile(Object object,long offset,double value){putDouble(object,offset,value);}
    public int getIntUnaligned(Object object,long offset){return getInt(object,offset);}public long getLongUnaligned(Object object,long offset){return getLong(object,offset);}
    public void putIntUnaligned(Object object,long offset,int value){putInt(object,offset,value);}public void putLongUnaligned(Object object,long offset,long value){putLong(object,offset,value);}
}
