package port.compat.java.nio.channels;

import java.io.IOException;

/** No implementation is instantiated: lock acquisition reports unsupported OS semantics. */
public abstract class FileLock implements AutoCloseable {
    public abstract boolean isValid();
    public abstract void release() throws IOException;
    public void close() throws IOException { release(); }
}
