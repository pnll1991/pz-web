package port.compat.java.io;
import java.io.*;
/** Java object serialization is unavailable; never emit a pretend stream. */
public class ObjectOutputStream extends OutputStream implements ObjectOutput,ObjectStreamConstants {
    protected ObjectOutputStream()throws IOException{throw unavailable();}
    public ObjectOutputStream(OutputStream stream)throws IOException{java.util.Objects.requireNonNull(stream);throw unavailable();}
    private static IOException unavailable(){return new IOException("ObjectOutputStream: Java serialization is not implemented by this browser runtime");}
    public void writeObject(Object object)throws IOException{throw unavailable();}
    protected void writeObjectOverride(Object object)throws IOException{throw unavailable();}
    public void writeUnshared(Object object)throws IOException{throw unavailable();}
    public void defaultWriteObject()throws IOException{throw unavailable();}
    public void writeFields()throws IOException{throw unavailable();}
    public void reset()throws IOException{throw unavailable();}
    public void useProtocolVersion(int version)throws IOException{throw unavailable();}
    public void write(int value)throws IOException{throw unavailable();}public void write(byte[] b)throws IOException{throw unavailable();}public void write(byte[] b,int offset,int length)throws IOException{throw unavailable();}
    public void flush()throws IOException{throw unavailable();}public void close()throws IOException{throw unavailable();}
    public void writeBoolean(boolean value)throws IOException{throw unavailable();}public void writeByte(int value)throws IOException{throw unavailable();}public void writeShort(int value)throws IOException{throw unavailable();}public void writeChar(int value)throws IOException{throw unavailable();}public void writeInt(int value)throws IOException{throw unavailable();}public void writeLong(long value)throws IOException{throw unavailable();}public void writeFloat(float value)throws IOException{throw unavailable();}public void writeDouble(double value)throws IOException{throw unavailable();}public void writeBytes(String value)throws IOException{throw unavailable();}public void writeChars(String value)throws IOException{throw unavailable();}public void writeUTF(String value)throws IOException{throw unavailable();}
}
