package port.compat.java.io;

import java.io.*;

public interface ObjectOutput extends DataOutput, AutoCloseable {
    void writeObject(Object value) throws IOException;
    void flush() throws IOException;
    void close() throws IOException;
}
