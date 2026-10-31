package port.runtime;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Round 79: read-only "zip filesystem" for FileSystems.newFileSystem(Path).
 *
 * The JDK resolves that call through the jdk.zipfs provider; TeaVM's classlib has no provider at
 * all, so zombie.worldMap.ImagePyramid.openZipFile (spawn map pyramids, world map) failed with
 * ProviderNotFoundException. This implements the subset the JDK contract defines for a read-only zip
 * filesystem: getPath, exists/isDirectory/readAttributes, newDirectoryStream and newInputStream.
 * Entries are read with java.util.zip.ZipFile over the local file (on-demand ranges in the browser).
 * Every mutation raises ReadOnlyFileSystemException, as zipfs does for a read-only archive.
 */
public final class ZipFileSystem extends FileSystem {
    private final Provider provider = new Provider();
    private final ZipFile zip;
    private final String archive;
    private final Map<String, ZipEntry> files = new HashMap<>();
    private final Map<String, TreeSet<String>> children = new HashMap<>();
    private boolean open = true;

    public static FileSystem open(Path archive) throws IOException {
        return new ZipFileSystem(archive.toAbsolutePath().toString());
    }

    private ZipFileSystem(String archive) throws IOException {
        this.archive = archive;
        this.zip = new ZipFile(new File(archive));
        children.put("/", new TreeSet<>());
        for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
            ZipEntry entry = e.nextElement();
            String name = "/" + entry.getName();
            boolean directory = name.endsWith("/");
            if (directory) name = name.substring(0, name.length() - 1);
            if (name.isEmpty()) continue;
            if (!directory) files.put(name, entry);
            else children.computeIfAbsent(name, k -> new TreeSet<>());
            for (String child = name, parent = parentOf(name); parent != null; child = parent, parent = parentOf(parent)) {
                children.computeIfAbsent(parent, k -> new TreeSet<>()).add(child.substring(child.lastIndexOf('/') + 1));
            }
        }
    }

    static String parentOf(String absolute) {
        if (absolute.equals("/")) return null;
        int slash = absolute.lastIndexOf('/');
        return slash <= 0 ? "/" : absolute.substring(0, slash);
    }

    @Override public FileSystemProvider provider() { return provider; }
    @Override public void close() throws IOException { if (open) { open = false; zip.close(); } }
    @Override public boolean isOpen() { return open; }
    @Override public boolean isReadOnly() { return true; }
    @Override public String getSeparator() { return "/"; }
    @Override public Iterable<Path> getRootDirectories() { return List.of(new ZipPath(this, "/")); }
    @Override public Iterable<FileStore> getFileStores() { throw new UnsupportedOperationException("zip file stores"); }
    @Override public Set<String> supportedFileAttributeViews() { return Set.of("basic"); }
    @Override public Path getPath(String first, String... more) {
        StringBuilder path = new StringBuilder(first);
        for (String part : more) { if (part.isEmpty()) continue; if (path.length() > 0) path.append('/'); path.append(part); }
        return new ZipPath(this, path.toString());
    }
    @Override public PathMatcher getPathMatcher(String syntaxAndPattern) { throw new UnsupportedOperationException("zip path matcher"); }
    @Override public UserPrincipalLookupService getUserPrincipalLookupService() { throw new UnsupportedOperationException(); }
    @Override public WatchService newWatchService() { throw new UnsupportedOperationException("zip watch service"); }
    @Override public String toString() { return archive; }

    private void checkOpen() { if (!open) throw new java.nio.file.ClosedFileSystemException(); }

    private ZipPath check(Path path) {
        if (!(path instanceof ZipPath zp) || zp.fs != this) throw new IllegalArgumentException("Path does not belong to this zip filesystem: " + path); // ProviderMismatchException is absent in TeaVM
        checkOpen();
        return zp;
    }

    /** Normalized absolute name inside the archive ("/" for the root). */
    private static String key(ZipPath path) {
        ZipPath abs = (ZipPath) path.toAbsolutePath().normalize();
        return abs.path.length() > 1 && abs.path.endsWith("/") ? abs.path.substring(0, abs.path.length() - 1) : abs.path;
    }

    private final class Provider extends FileSystemProvider {
        @Override public String getScheme() { return "jar"; }
        @Override public FileSystem newFileSystem(URI uri, Map<String, ?> env) { throw new UnsupportedOperationException("zip by URI"); }
        @Override public FileSystem getFileSystem(URI uri) { throw new UnsupportedOperationException("zip by URI"); }
        @Override public Path getPath(URI uri) { throw new UnsupportedOperationException("zip by URI"); }
        @Override public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
            throw new UnsupportedOperationException("zip byte channels");
        }
        @Override public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
            String name = key(check(path));
            ZipEntry entry = files.get(name);
            if (entry == null) throw new NoSuchFileException(path.toString());
            return zip.getInputStream(entry);
        }
        @Override public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            ZipPath zp = check(dir);
            String name = key(zp);
            TreeSet<String> names = children.get(name);
            if (names == null) {
                if (files.containsKey(name)) throw new NotDirectoryException(dir.toString());
                throw new NoSuchFileException(dir.toString());
            }
            List<Path> out = new ArrayList<>();
            for (String child : names) {
                Path p = zp.resolve(child);
                if (filter.accept(p)) out.add(p);
            }
            return new DirectoryStream<>() {
                @Override public Iterator<Path> iterator() { return out.iterator(); }
                @Override public void close() { }
            };
        }
        @Override public void createDirectory(Path dir, FileAttribute<?>... attrs) { throw new ReadOnlyFileSystemException(); }
        @Override public void delete(Path path) { throw new ReadOnlyFileSystemException(); }
        @Override public void copy(Path source, Path target, CopyOption... options) { throw new ReadOnlyFileSystemException(); }
        @Override public void move(Path source, Path target, CopyOption... options) { throw new ReadOnlyFileSystemException(); }
        @Override public boolean isSameFile(Path a, Path b) { return a.equals(b); }
        @Override public boolean isHidden(Path path) { return false; }
        @Override public FileStore getFileStore(Path path) { throw new UnsupportedOperationException("zip file stores"); }
        @Override public void checkAccess(Path path, AccessMode... modes) throws IOException {
            String name = key(check(path));
            if (!files.containsKey(name) && !children.containsKey(name)) throw new NoSuchFileException(path.toString());
            for (AccessMode mode : modes) if (mode == AccessMode.WRITE) throw new ReadOnlyFileSystemException();
        }
        @Override public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) { return null; }
        @SuppressWarnings("unchecked")
        @Override public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
            String name = key(check(path));
            ZipEntry entry = files.get(name);
            boolean directory = entry == null && children.containsKey(name);
            if (entry == null && !directory) throw new NoSuchFileException(path.toString());
            long size = entry == null ? 0 : entry.getSize();
            FileTime time = FileTime.fromMillis(entry == null ? 0 : Math.max(0, entry.getTime()));
            return (A) new BasicFileAttributes() {
                @Override public FileTime lastModifiedTime() { return time; }
                @Override public FileTime lastAccessTime() { return time; }
                @Override public FileTime creationTime() { return time; }
                @Override public boolean isRegularFile() { return !directory; }
                @Override public boolean isDirectory() { return directory; }
                @Override public boolean isSymbolicLink() { return false; }
                @Override public boolean isOther() { return false; }
                @Override public long size() { return size; }
                @Override public Object fileKey() { return null; }
            };
        }
        @Override public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
            throw new UnsupportedOperationException("zip attribute maps");
        }
        @Override public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
            throw new ReadOnlyFileSystemException();
        }
    }

    /** Path inside the archive; "/" separated, absolute when it starts with "/". */
    static final class ZipPath implements Path {
        final ZipFileSystem fs;
        final String path;

        ZipPath(ZipFileSystem fs, String path) {
            String p = path;
            while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
            while (p.contains("//")) p = p.replace("//", "/");
            this.fs = fs; this.path = p;
        }

        private String[] names() {
            String body = path.startsWith("/") ? path.substring(1) : path;
            return body.isEmpty() ? new String[0] : body.split("/");
        }

        @Override public FileSystem getFileSystem() { return fs; }
        @Override public boolean isAbsolute() { return path.startsWith("/"); }
        @Override public Path getRoot() { return isAbsolute() ? new ZipPath(fs, "/") : null; }
        @Override public Path getFileName() {
            String[] n = names();
            return n.length == 0 ? null : new ZipPath(fs, n[n.length - 1]);
        }
        @Override public Path getParent() {
            String[] n = names();
            if (n.length == 0) return null;
            if (n.length == 1) return isAbsolute() ? new ZipPath(fs, "/") : null;
            return new ZipPath(fs, path.substring(0, path.lastIndexOf('/')));
        }
        @Override public int getNameCount() { return names().length; }
        @Override public Path getName(int index) { return new ZipPath(fs, names()[index]); }
        @Override public Path subpath(int begin, int end) {
            String[] n = names();
            if (begin < 0 || end > n.length || begin >= end) throw new IllegalArgumentException();
            return new ZipPath(fs, String.join("/", java.util.Arrays.copyOfRange(n, begin, end)));
        }
        @Override public boolean startsWith(Path other) {
            if (!(other instanceof ZipPath o) || o.fs != fs || o.isAbsolute() != isAbsolute()) return false;
            String[] a = names(), b = o.names();
            if (b.length > a.length) return false;
            for (int i = 0; i < b.length; i++) if (!a[i].equals(b[i])) return false;
            return true;
        }
        @Override public boolean endsWith(Path other) {
            if (!(other instanceof ZipPath o) || o.fs != fs) return false;
            if (o.isAbsolute()) return equals(o);
            String[] a = names(), b = o.names();
            if (b.length > a.length) return false;
            for (int i = 0; i < b.length; i++) if (!a[a.length - b.length + i].equals(b[i])) return false;
            return true;
        }
        @Override public Path normalize() {
            List<String> out = new ArrayList<>();
            for (String n : names()) {
                if (n.equals(".")) continue;
                if (n.equals("..") && !out.isEmpty() && !out.get(out.size() - 1).equals("..")) { out.remove(out.size() - 1); continue; }
                if (n.equals("..") && isAbsolute()) continue;
                out.add(n);
            }
            return new ZipPath(fs, (isAbsolute() ? "/" : "") + String.join("/", out));
        }
        @Override public Path resolve(Path other) {
            ZipPath o = (ZipPath) other;
            if (o.isAbsolute()) return o;
            if (o.path.isEmpty()) return this;
            if (path.isEmpty()) return o;
            return new ZipPath(fs, path.equals("/") ? "/" + o.path : path + "/" + o.path);
        }
        @Override public Path relativize(Path other) {
            ZipPath o = (ZipPath) other;
            String[] a = names(), b = o.names();
            int common = 0;
            while (common < a.length && common < b.length && a[common].equals(b[common])) common++;
            List<String> out = new ArrayList<>();
            for (int i = common; i < a.length; i++) out.add("..");
            for (int i = common; i < b.length; i++) out.add(b[i]);
            return new ZipPath(fs, String.join("/", out));
        }
        @Override public URI toUri() { throw new UnsupportedOperationException("zip path URIs"); }
        @Override public Path toAbsolutePath() { return isAbsolute() ? this : new ZipPath(fs, "/" + path); }
        @Override public Path toRealPath(LinkOption... options) { return toAbsolutePath().normalize(); }
        @Override public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
            throw new UnsupportedOperationException("zip watch service");
        }
        @Override public int compareTo(Path other) { return path.compareTo(((ZipPath) other).path); }
        @Override public boolean equals(Object other) { return other instanceof ZipPath o && o.fs == fs && o.path.equals(path); }
        @Override public int hashCode() { return path.hashCode(); }
        @Override public String toString() { return path; }
    }
}
