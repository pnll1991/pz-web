package port.compat.java.lang;

import java.io.*;

/** Process contract only. Browser ProcessBuilder/Runtime never create one. */
public abstract class Process {
    public abstract OutputStream getOutputStream();
    public abstract InputStream getInputStream();
    public abstract InputStream getErrorStream();
    public abstract int waitFor() throws InterruptedException;
    public abstract int exitValue();
    public abstract void destroy();
}
