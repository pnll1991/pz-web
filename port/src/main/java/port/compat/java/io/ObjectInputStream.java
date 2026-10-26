package port.compat.java.io;
import java.io.*;
public class ObjectInputStream extends InputStream implements ObjectInput {
    protected ObjectInputStream()throws IOException{throw unavailable();}
    public ObjectInputStream(InputStream stream)throws IOException{java.util.Objects.requireNonNull(stream);throw unavailable();}
    private static IOException unavailable(){return new IOException("ObjectInputStream: Java serialization is not implemented by this browser runtime");}
    public Object readObject()throws IOException,ClassNotFoundException{throw unavailable();}protected Object readObjectOverride()throws IOException,ClassNotFoundException{throw unavailable();}
    public void defaultReadObject()throws IOException,ClassNotFoundException{throw unavailable();}
    protected Class<?> resolveClass(ObjectStreamClass description)throws IOException,ClassNotFoundException{throw unavailable();}
    protected Class<?> resolveProxyClass(String[] names)throws IOException,ClassNotFoundException{throw unavailable();}
    public int read()throws IOException{throw unavailable();}public int read(byte[] b)throws IOException{throw unavailable();}public int read(byte[] b,int o,int n)throws IOException{throw unavailable();}
    public long skip(long n)throws IOException{throw unavailable();}public int available()throws IOException{throw unavailable();}public void close()throws IOException{throw unavailable();}
    public void readFully(byte[] b)throws IOException{throw unavailable();}public void readFully(byte[] b,int o,int n)throws IOException{throw unavailable();}public int skipBytes(int n)throws IOException{throw unavailable();}
    public boolean readBoolean()throws IOException{throw unavailable();}public byte readByte()throws IOException{throw unavailable();}public int readUnsignedByte()throws IOException{throw unavailable();}public short readShort()throws IOException{throw unavailable();}public int readUnsignedShort()throws IOException{throw unavailable();}public char readChar()throws IOException{throw unavailable();}public int readInt()throws IOException{throw unavailable();}public long readLong()throws IOException{throw unavailable();}public float readFloat()throws IOException{throw unavailable();}public double readDouble()throws IOException{throw unavailable();}public String readLine()throws IOException{throw unavailable();}public String readUTF()throws IOException{throw unavailable();}
}
