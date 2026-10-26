package port.compat.java.io;

import java.io.*;

/** Standard stream contract, with no fabricated serialization implementation. */
public interface ObjectInput extends DataInput, AutoCloseable {
    Object readObject() throws ClassNotFoundException, IOException;
    int read() throws IOException;
    int read(byte[] bytes) throws IOException;
    int read(byte[] bytes, int offset, int length) throws IOException;
    long skip(long count) throws IOException;
    int available() throws IOException;
    void close() throws IOException;
}
