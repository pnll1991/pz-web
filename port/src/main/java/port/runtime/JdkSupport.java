package port.runtime;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/** Portable API operations missing from the pinned TeaVM class library. */
public final class JdkSupport {
    public static Path toPath(File file) { return Paths.get(file.getPath()); }

    public static Path resolve(Path path, String first, String[] more) {
        Objects.requireNonNull(more);
        Path result = path.resolve(first);
        for (String part : more) result = result.resolve(part);
        return result;
    }

    public static long transferTo(Reader reader, Writer writer) throws IOException {
        Objects.requireNonNull(writer);
        char[] buffer = new char[8192];
        long total = 0;
        int count;
        while ((count = reader.read(buffer, 0, buffer.length)) != -1) {
            writer.write(buffer, 0, count);
            total += count;
        }
        return total;
    }

    public static void onSpinWait() { Thread.yield(); }

    public static int read(InputStream stream) throws IOException {
        byte[] one = new byte[1];
        return stream.read(one, 0, 1) < 0 ? -1 : one[0] & 255;
    }
}
