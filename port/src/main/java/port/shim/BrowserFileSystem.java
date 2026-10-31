package port.shim;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.VirtualFileSystem;
import org.teavm.runtime.fs.VirtualFileSystemProvider;

/** TeaVM java.io and java.nio use the same preloaded/persistent browser VFS. */
public final class BrowserFileSystem implements VirtualFileSystem {
    public static void install() {
        VirtualFileSystemProvider.setInstance(new BrowserFileSystem());
        System.setProperty("user.dir", "/");
        System.setProperty("user.home", "/");
    }
    @Override public String getUserDir() { return "/"; }
    @Override public boolean isWindows() { return false; }
    @Override public String[] getRoots() { return new String[]{"/"}; }
    @Override public String canonicalize(String path) { return normalize(path.startsWith("/") ? path : "/" + path); }
    @Override public VirtualFile getFile(String path) { return new Node(canonicalize(path)); }

    private static final class Node implements VirtualFile {
        final String path;
        Node(String path) { this.path = path; }
        private String child(String name) {
            if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0)
                throw new IllegalArgumentException("Invalid child name");
            return (path.equals("/") ? "" : path) + "/" + name;
        }
        @Override public String getName() { return path.substring(path.lastIndexOf('/') + 1); }
        @Override public boolean isDirectory() { return type(path) == 2; }
        @Override public boolean isFile() { return type(path) == 1; }
        @Override public boolean canRead() { return exists(); }
        @Override public boolean canWrite() { return writable(path); }
        @Override public int length() { return size(path); }
        @Override public long lastModified() { return (long) modified(path); }
        @Override public boolean setLastModified(long value) { return value >= 0 && setModified(path, value); }
        @Override public boolean setReadOnly(boolean value) { return readOnly(path, value); }
        @Override public String[] listFiles() {
            JSArray<JSString> names = children(path);
            if (names == null) return null;
            String[] result = new String[names.getLength()];
            for (int i = 0; i < result.length; i++) result[i] = names.get(i).stringValue();
            return result;
        }
        @Override public boolean createFile(String name) throws IOException {
            try { return create(child(name)); }
            catch (RuntimeException error) { throw new IOException("Cannot create file in " + path, error); }
        }
        @Override public boolean createDirectory(String name) { return mkdir(child(name)); }
        @Override public boolean delete() { return remove(path); }
        @Override public boolean adopt(VirtualFile file, String name) {
            return file instanceof Node source && rename(source.path, child(name));
        }
        @Override public VirtualFileAccessor createAccessor(boolean read, boolean write, boolean append) {
            if (!isFile() || (write && !canWrite())) return null;
            if (write && !read && !append) resizeFile(path, 0);
            return new Accessor(path, read, write, append);
        }
    }

    private static final class Accessor implements VirtualFileAccessor {
        final String path;
        final boolean readable, writable, append;
        int position;
        boolean closed;
        Accessor(String path, boolean read, boolean write, boolean append) {
            this.path = path; readable = read; writable = write; this.append = append;
            position = append ? BrowserFileSystem.size(path) : 0;
        }
        void check() throws IOException { if (closed) throw new IOException("File is closed: " + path); }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            check(); Objects.checkFromIndexSize(offset, length, bytes.length);
            if (!readable) throw new IOException("File is not open for reading: " + path);
            ByteBuffer buffer = ByteBuffer.allocateDirect(Math.min(length, 65536));
            try {
                int count = readBytes(path, position, Uint8Array.fromJavaBuffer(buffer));
                buffer.get(bytes, offset, count); position += count; return count;
            } catch (RuntimeException error) { throw new IOException("Read failed: " + path, error); }
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            check(); Objects.checkFromIndexSize(offset, length, bytes.length);
            if (!writable) throw new IOException("File is read only: " + path);
            if (append) position = BrowserFileSystem.size(path);
            if ((long) position + length > Integer.MAX_VALUE) throw new IOException("File exceeds wasm32 offset limit");
            try {
                ByteBuffer buffer = ByteBuffer.allocateDirect(length);
                buffer.put(bytes, offset, length).flip();
                writeBytes(path, position, Uint8Array.fromJavaBuffer(buffer)); position += length;
            } catch (RuntimeException error) { throw new IOException("Write failed: " + path, error); }
        }
        @Override public int tell() throws IOException { check(); return position; }
        @Override public void seek(int value) throws IOException {
            check(); if (value < 0) throw new IOException("Negative seek"); position = value;
        }
        @Override public void skip(int value) throws IOException {
            long next = (long) position + value;
            if (next < 0 || next > Integer.MAX_VALUE) throw new IOException("Invalid file offset");
            seek((int) next);
        }
        @Override public int size() throws IOException { check(); return BrowserFileSystem.size(path); }
        @Override public void resize(int value) throws IOException {
            check(); if (!writable || value < 0) throw new IOException("Cannot resize: " + path);
            try { resizeFile(path, value); position = Math.min(position, value); }
            catch (RuntimeException error) { throw new IOException("Resize failed: " + path, error); }
        }
        // Writes are immediately visible in memory. Persistence is an explicit
        // asynchronous host operation; this does not claim durable disk sync.
        @Override public void flush() throws IOException { check(); }
        @Override public void close() { closed = true; }
    }

    @JSBody(params="path", script="return pzRuntime.normalizePath(path);") private static native String normalize(String path);
    @JSBody(params="path", script="return pzRuntime.vfs.kind(path);") private static native int type(String path);
    @JSBody(params="path", script="return pzRuntime.vfs.canWrite(path);") private static native boolean writable(String path);
    @JSBody(params="path", script="return pzRuntime.vfs.size(path);") private static native int size(String path);
    @JSBody(params="path", script="return pzRuntime.vfs.lastModified(path);") private static native double modified(String path);
    @JSBody(params={"path","value"}, script="return pzRuntime.vfs.setAttribute(path,'modified',value);") private static native boolean setModified(String path, double value);
    @JSBody(params={"path","value"}, script="return pzRuntime.vfs.setAttribute(path,'readOnly',value);") private static native boolean readOnly(String path, boolean value);
    @JSBody(params="path", script="return pzRuntime.vfs.children(path);") private static native JSArray<JSString> children(String path);
    @JSBody(params="path", script="return pzRuntime.vfs.createFile(path);") private static native boolean create(String path);
    @JSBody(params="path", script="return pzRuntime.vfs.mkdir(path);") private static native boolean mkdir(String path);
    @JSBody(params="path", script="try {pzRuntime.vfs.remove(path);return true;}catch(e){return false;}") private static native boolean remove(String path);
    @JSBody(params={"from","to"}, script="try {pzRuntime.vfs.rename(from,to);return true;}catch(e){return false;}") private static native boolean rename(String from, String to);
    @JSBody(params={"path","length"}, script="pzRuntime.vfs.resize(path,length);") private static native void resizeFile(String path, int length);
    @JSBody(params={"path","position","buffer"}, script="return pzRuntime.vfs.readInto(path,position,buffer);") private static native int readBytes(String path, int position, Uint8Array buffer);
    @JSBody(params={"path","position","buffer"}, script="pzRuntime.vfs.writeAt(path,position,buffer);") private static native void writeBytes(String path, int position, Uint8Array buffer);
}
