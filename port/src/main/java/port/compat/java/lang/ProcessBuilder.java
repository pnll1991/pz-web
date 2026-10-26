package port.compat.java.lang;

import java.io.*;
import java.util.*;

/** Preserve builder configuration; OS execution is explicitly unavailable. */
public final class ProcessBuilder {
    private List<String> command;
    private File directory;
    private boolean mergeError;
    private final Map<String,String> environment = new HashMap<>();
    public ProcessBuilder(String... command) { command(command); }
    public ProcessBuilder(List<String> command) { command(command); }
    public ProcessBuilder command(String... command) { this.command = new ArrayList<>(Arrays.asList(command)); return this; }
    public ProcessBuilder command(List<String> command) { this.command = Objects.requireNonNull(command); return this; }
    public List<String> command() { return command; }
    public Map<String,String> environment() { return environment; }
    public ProcessBuilder directory(File value) { directory = value; return this; }
    public File directory() { return directory; }
    public ProcessBuilder redirectErrorStream(boolean value) { mergeError = value; return this; }
    public boolean redirectErrorStream() { return mergeError; }
    public Process start() throws IOException {
        throw new IOException("ProcessBuilder.start: OS process execution is unavailable in the browser");
    }
}
