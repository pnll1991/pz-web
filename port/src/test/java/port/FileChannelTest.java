package port;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.teavm.runtime.fs.VirtualFileAccessor;
import static org.junit.jupiter.api.Assertions.*;

class FileChannelTest {
    @TempDir Path directory;
    private static final class DiskAccessor implements VirtualFileAccessor {
        final RandomAccessFile file;
        DiskAccessor(Path path) throws IOException { file = new RandomAccessFile(path.toFile(), "rw"); }
        public int read(byte[] bytes, int start, int count) throws IOException { return Math.max(0,file.read(bytes,start,count)); }
        public void write(byte[] bytes, int start, int count) throws IOException { file.write(bytes,start,count); }
        public int tell() throws IOException { return (int)file.getFilePointer(); }
        public void seek(int position) throws IOException { file.seek(position); }
        public void skip(int count) throws IOException { file.seek(file.getFilePointer()+count); }
        public int size() throws IOException { return (int)file.length(); }
        public void resize(int size) throws IOException { file.setLength(size); }
        public void flush() throws IOException { file.getFD().sync(); }
        public void close() throws IOException { file.close(); }
    }
    @Test void positionalIOTransferAndCloseFollowChannelContract() throws Exception {
        byte[] expected = new byte[180000]; new Random(42).nextBytes(expected);
        var accessor = new DiskAccessor(directory.resolve("channel.bin"));
        var channel = new port.compat.java.nio.channels.FileChannel(accessor,true,true,null);
        try {
            var source = ByteBuffer.wrap(expected);
            while (source.hasRemaining()) channel.write(source);
            channel.position(23);
            var piece = ByteBuffer.allocate(10); assertEquals(10,channel.read(piece,17));
            assertArrayEquals(java.util.Arrays.copyOfRange(expected,17,27),piece.array());
            assertEquals(23,channel.position());
            var result = new ByteArrayOutputStream();
            WritableByteChannel shortWriter = new WritableByteChannel() {
                public int write(ByteBuffer data) { int count=Math.min(17,data.remaining()); for(int i=0;i<count;i++)result.write(data.get()); return count; }
                public boolean isOpen() { return true; }
                public void close() { }
            };
            assertEquals(expected.length,channel.transferTo(0,expected.length+500L,shortWriter));
            assertArrayEquals(expected,result.toByteArray()); assertEquals(23,channel.position());
            channel.truncate(expected.length+50L); assertEquals(expected.length,channel.size());
            channel.position(expected.length); assertEquals(-1,channel.read(ByteBuffer.allocate(1)));
            assertEquals(0,channel.read(ByteBuffer.allocate(0))); channel.force(true);
        } finally { channel.close(); }
        assertFalse(channel.isOpen()); assertThrows(ClosedChannelException.class,()->channel.read(ByteBuffer.allocate(1)));
    }
    @Test void channelCannotWriteThroughReadOnlyHandle() throws Exception {
        try (var channel = new port.compat.java.nio.channels.FileChannel(new DiskAccessor(directory.resolve("readonly.bin")),true,false,null)) {
            assertThrows(NonWritableChannelException.class,()->channel.write(ByteBuffer.wrap(new byte[]{1})));
            assertThrows(NonWritableChannelException.class,()->channel.truncate(0));
            assertThrows(IllegalArgumentException.class,()->channel.position(-1));
            assertThrows(IOException.class,()->channel.position(1L<<33));
        }
    }
}
