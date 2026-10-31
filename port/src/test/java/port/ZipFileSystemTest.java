package port;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import port.runtime.ZipFileSystem;

/** Round 79: read-only zip filesystem used by FileSystems.newFileSystem (synthetic archive). */
class ZipFileSystemTest {
    @Test void listsReadsAndRejectsWrites(@TempDir Path dir) throws Exception {
        Path archive = dir.resolve("pyramid.zip");
        try (var out = new ZipOutputStream(new FileOutputStream(archive.toFile()))) {
            out.putNextEntry(new ZipEntry("pyramid.txt")); out.write("levels 2".getBytes()); out.closeEntry();
            out.putNextEntry(new ZipEntry("0/")); out.closeEntry();
            out.putNextEntry(new ZipEntry("0/1_2.png")); out.write(new byte[] { 1, 2, 3 }); out.closeEntry();
            out.putNextEntry(new ZipEntry("1/0_0.png")); out.write(new byte[] { 4 }); out.closeEntry();
        }
        try (FileSystem fs = ZipFileSystem.open(archive)) {
            assertTrue(Files.exists(fs.getPath("pyramid.txt")));
            assertTrue(Files.isDirectory(fs.getPath("0")));
            assertTrue(Files.isDirectory(fs.getPath("/1")), "implicit directory");
            assertFalse(Files.exists(fs.getPath("missing.png")));
            assertEquals("levels 2", new String(Files.newInputStream(fs.getPath("pyramid.txt")).readAllBytes()));
            assertArrayEquals(new byte[] { 1, 2, 3 }, Files.newInputStream(fs.getPath("0", "1_2.png")).readAllBytes());
            List<String> names = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fs.getPath("/"))) {
                for (Path p : stream) names.add(p.getFileName().toString());
            }
            assertEquals(List.of("0", "1", "pyramid.txt"), names);
            assertEquals("1_2.png", fs.getPath("0/1_2.png").getFileName().toString());
            assertThrows(ReadOnlyFileSystemException.class, () -> Files.delete(fs.getPath("pyramid.txt")));
        }
    }
}
