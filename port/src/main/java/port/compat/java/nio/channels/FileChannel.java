package port.compat.java.nio.channels;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.*;
import org.teavm.classlib.PlatformDetector;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.VirtualFileSystemProvider;
import port.shim.BrowserPersistence;

/** File channels share the very same VFS accessor as their java.io stream. */
public class FileChannel implements SeekableByteChannel {
    private final VirtualFileAccessor accessor;
    private final Closeable owner;
    private final boolean readable, writable;
    private boolean closed;
    public FileChannel(VirtualFileAccessor accessor, boolean readable, boolean writable, Closeable owner) {
        this.accessor = accessor; this.readable = readable; this.writable = writable; this.owner = owner;
        closed = accessor == null;
    }
    public static FileChannel open(Path path, OpenOption... options) throws IOException {
        return open(path, new HashSet<>(Arrays.asList(options)));
    }
    public static FileChannel open(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes) throws IOException {
        Objects.requireNonNull(path); Objects.requireNonNull(options);
        if (attributes.length != 0) throw new UnsupportedOperationException("FileChannel.open: POSIX attributes unavailable in VFS");
        var flags = new HashSet<OpenOption>(options);
        for (var option : flags) if (!(option instanceof StandardOpenOption) || option == StandardOpenOption.DELETE_ON_CLOSE
                || option == StandardOpenOption.SYNC || option == StandardOpenOption.DSYNC || option == StandardOpenOption.SPARSE)
            throw new UnsupportedOperationException("FileChannel.open option: " + option);
        boolean append = flags.contains(StandardOpenOption.APPEND);
        boolean write = append || flags.contains(StandardOpenOption.WRITE);
        boolean read = flags.contains(StandardOpenOption.READ) || !write;
        if (append && (read || flags.contains(StandardOpenOption.TRUNCATE_EXISTING))) throw new IllegalArgumentException("APPEND with READ/TRUNCATE_EXISTING");
        var fs = VirtualFileSystemProvider.getInstance();
        String name = fs.canonicalize(path.toAbsolutePath().normalize().toString());
        var file = fs.getFile(name);
        if (file.isFile() || file.isDirectory()) {
            if (write && flags.contains(StandardOpenOption.CREATE_NEW)) throw new FileAlreadyExistsException(name);
            if (file.isDirectory()) throw new IOException("Not a regular file: " + name);
        } else {
            if (!write || !(flags.contains(StandardOpenOption.CREATE) || flags.contains(StandardOpenOption.CREATE_NEW))) throw new NoSuchFileException(name);
            int slash = name.lastIndexOf('/');
            var parent = fs.getFile(slash == 0 ? "/" : name.substring(0, slash));
            if (!parent.isDirectory() || !parent.createFile(name.substring(slash + 1))) throw new IOException("Cannot create: " + name);
        }
        // Opening for write alone must not truncate. The channel enforces read permission.
        var accessor = file.createAccessor(true, write, append);
        if (accessor == null) throw new IOException("Cannot open: " + name);
        if (write && flags.contains(StandardOpenOption.TRUNCATE_EXISTING)) accessor.resize(0);
        return new FileChannel(accessor, read, write, null);
    }
    private void check() throws IOException { if (!isOpen()) throw new ClosedChannelException(); }
    private static int offset(long position) throws IOException {
        if (position < 0) throw new IllegalArgumentException("Negative file position");
        if (position > Integer.MAX_VALUE) throw new IOException("VFS file exceeds wasm32 offset limit");
        return (int) position;
    }
    public boolean isOpen() {
        if (closed) return false;
        try { accessor.tell(); return true; } catch (IOException error) { return false; }
    }
    public void close() throws IOException {
        if (!closed) { closed = true; if (owner != null) owner.close(); else accessor.close(); }
    }
    public synchronized long position() throws IOException { check(); return accessor.tell(); }
    public synchronized FileChannel position(long position) throws IOException { check(); accessor.seek(offset(position)); return this; }
    public synchronized long size() throws IOException { check(); return accessor.size(); }
    public synchronized FileChannel truncate(long length) throws IOException {
        check(); if (!writable) throw new NonWritableChannelException();
        int value = offset(length); if (value < accessor.size()) accessor.resize(value);
        return this;
    }
    public synchronized int read(ByteBuffer target) throws IOException {
        check(); if (!readable) throw new NonReadableChannelException();
        if (target.isReadOnly()) throw new IllegalArgumentException("Read-only target buffer");
        if (!target.hasRemaining()) return 0;
        byte[] bytes = new byte[Math.min(65536, target.remaining())];
        int count = accessor.read(bytes, 0, bytes.length);
        if (count <= 0) return -1;
        target.put(bytes, 0, count); return count;
    }
    public synchronized int write(ByteBuffer source) throws IOException {
        check(); if (!writable) throw new NonWritableChannelException();
        int count = Math.min(source.remaining(), 65536);
        byte[] bytes = new byte[count]; source.duplicate().get(bytes);
        accessor.write(bytes, 0, count); source.position(source.position() + count); return count;
    }
    public synchronized int read(ByteBuffer target, long position) throws IOException {
        check(); int saved = accessor.tell();
        try { accessor.seek(offset(position)); return read(target); } finally { accessor.seek(saved); }
    }
    public synchronized int write(ByteBuffer source, long position) throws IOException {
        check(); int saved = accessor.tell();
        try { accessor.seek(offset(position)); return write(source); } finally { accessor.seek(saved); }
    }
    public long read(ByteBuffer[] targets) throws IOException { return read(targets, 0, targets.length); }
    public synchronized long read(ByteBuffer[] targets, int start, int length) throws IOException {
        Objects.checkFromIndexSize(start, length, targets.length); check(); if (!readable) throw new NonReadableChannelException();
        long total = 0;
        for (int i = start; i < start + length; i++) while (targets[i].hasRemaining()) {
            int count = read(targets[i]); if (count < 0) return total == 0 ? -1 : total; if (count == 0) return total; total += count;
        }
        return total;
    }
    public long write(ByteBuffer[] sources) throws IOException { return write(sources, 0, sources.length); }
    public synchronized long write(ByteBuffer[] sources, int start, int length) throws IOException {
        Objects.checkFromIndexSize(start, length, sources.length); check(); if (!writable) throw new NonWritableChannelException();
        long total = 0;
        for (int i = start; i < start + length; i++) while (sources[i].hasRemaining()) {
            int count = write(sources[i]); if (count == 0) return total; total += count;
        }
        return total;
    }
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        check(); if (!readable) throw new NonReadableChannelException(); offset(position);
        if (count < 0) throw new IllegalArgumentException("Negative transfer count");
        long total = 0; ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(count, 65536));
        while (total < count) {
            buffer.clear().limit((int) Math.min(buffer.capacity(), count - total));
            if (read(buffer, position + total) < 0) break;
            buffer.flip();
            while (buffer.hasRemaining()) { int written = target.write(buffer); if (written == 0) return total; total += written; }
        }
        return total;
    }
    public long transferFrom(ReadableByteChannel source, long position, long count) throws IOException {
        check(); if (!writable) throw new NonWritableChannelException(); offset(position);
        if (count < 0) throw new IllegalArgumentException("Negative transfer count");
        if (position > size()) return 0;
        long total = 0; ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(count, 65536));
        while (total < count) {
            buffer.clear().limit((int) Math.min(buffer.capacity(), count - total));
            if (source.read(buffer) <= 0) break;
            buffer.flip(); while (buffer.hasRemaining()) total += write(buffer, position + total);
        }
        return total;
    }
    public void force(boolean metadata) throws IOException {
        check();
        if (PlatformDetector.isWebAssemblyGC()) BrowserPersistence.flush(); else accessor.flush();
    }
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        check();
        throw new UnsupportedOperationException("FileChannel.lock: OS byte-range locks are unavailable in the browser VFS");
    }
    public FileLock lock() throws IOException { return lock(0,Long.MAX_VALUE,false); }
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        check();
        throw new UnsupportedOperationException("FileChannel.tryLock: OS byte-range locks are unavailable in the browser VFS");
    }
    public FileLock tryLock() throws IOException { return tryLock(0,Long.MAX_VALUE,false); }
}
