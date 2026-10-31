package port;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import port.runtime.memory.NativeArena;
import port.runtime.memory.NativeMemory;
import port.shim.BrowserStbWrite;

/** Synthetic images through the stbi_write_png bridge, decoded back with ImageIO. */
class StbImageWriteTest {
    @TempDir Path dir;

    @AfterEach void reset() {
        BrowserStbWrite.setFlip(0);
        NativeMemory.memPutInt(BrowserStbWrite.compressionLevelAddress(), 8);
        NativeMemory.memPutInt(BrowserStbWrite.forcePngFilterAddress(), -1);
    }

    private static int sample(int x, int y, int channel) { return (x * 37 + y * 101 + channel * 59 + x * y) & 0xff; }

    /** Pixels at the given stride with padding bytes set to 0xEE so a stride bug changes the output. */
    private static long image(int width, int height, int comp, int stride) {
        long data = NativeArena.allocate((long)stride * height, 8);
        NativeMemory.memSet(data, 0xEE, (long)stride * height);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                for (int c = 0; c < comp; c++)
                    NativeMemory.memPutByte(data + (long)y * stride + x * comp + c, (byte)sample(x, y, c));
        return data;
    }

    private static long cString(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        long address = NativeArena.allocate(bytes.length + 1, 1);
        NativeMemory.memCopy(bytes, address);
        return address;
    }

    private BufferedImage write(String name, int width, int height, int comp, long data, int stride) throws Exception {
        Path file = dir.resolve(name);
        long path = cString(file.toString());
        try {
            assertEquals(1, BrowserStbWrite.writePng(path, width, height, comp, data, stride));
        } finally { NativeArena.free(path); }
        byte[] bytes = Files.readAllBytes(file);
        assertArrayEquals(new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10}, java.util.Arrays.copyOf(bytes, 8));
        assertEquals(comp == 1 ? 0 : comp == 2 ? 4 : comp == 3 ? 2 : 6, bytes[25], "IHDR colour type");
        assertEquals(8, bytes[24], "IHDR bit depth");
        var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(decoded);
        assertEquals(width, decoded.getWidth());
        assertEquals(height, decoded.getHeight());
        return decoded;
    }

    private static void assertPixels(BufferedImage decoded, int width, int height, int comp, boolean flipped) {
        var raster = decoded.getRaster();
        assertEquals(comp, raster.getNumBands());
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                for (int c = 0; c < comp; c++)
                    assertEquals(sample(x, flipped ? height - 1 - y : y, c), raster.getSample(x, y, c),
                        "pixel " + x + "," + y + " channel " + c);
    }

    @Test void everyComponentCountRoundTrips() throws Exception {
        for (int comp = 1; comp <= 4; comp++) {
            int width = 23, height = 17;
            long data = image(width, height, comp, width * comp);
            try {
                assertPixels(write("c" + comp + ".png", width, height, comp, data, width * comp), width, height, comp, false);
                // stride 0 means tightly packed, as in stbiw__png_to_mem.
                assertPixels(write("c" + comp + "z.png", width, height, comp, data, 0), width, height, comp, false);
            } finally { NativeArena.free(data); }
        }
    }

    @Test void paddedStrideSkipsTheGap() throws Exception {
        int width = 13, height = 9, comp = 3, stride = width * comp + 11;
        long data = image(width, height, comp, stride);
        try {
            assertPixels(write("stride.png", width, height, comp, data, stride), width, height, comp, false);
        } finally { NativeArena.free(data); }
    }

    @Test void flipOnWriteAndThumbnailShape() throws Exception {
        // NativeImage.writePng: comp 4, stride width*4, flip true (Core.TakeScreenshot thumb.png).
        int width = 64, height = 40, comp = 4;
        long data = image(width, height, comp, width * comp);
        try {
            BrowserStbWrite.setFlip(1);
            assertPixels(write("thumb.png", width, height, comp, data, width * comp), width, height, comp, true);
        } finally { NativeArena.free(data); }
    }

    @Test void globalsAreRealCellsWithStbDefaults() throws Exception {
        assertEquals(8, NativeMemory.memGetInt(BrowserStbWrite.compressionLevelAddress()));
        assertEquals(-1, NativeMemory.memGetInt(BrowserStbWrite.forcePngFilterAddress()));
        assertEquals(1, NativeMemory.memGetInt(BrowserStbWrite.tgaWithRleAddress()));
        assertEquals(0L, NativeMemory.memGetLong(BrowserStbWrite.zlibCompressAddress()));
        assertEquals(BrowserStbWrite.compressionLevelAddress(), BrowserStbWrite.compressionLevelAddress());
        int width = 31, height = 21, comp = 4;
        long data = image(width, height, comp, width * comp);
        try {
            NativeMemory.memPutInt(BrowserStbWrite.compressionLevelAddress(), 0);
            BufferedImage stored = write("level0.png", width, height, comp, data, 0);
            assertPixels(stored, width, height, comp, false);
            long storedSize = Files.size(dir.resolve("level0.png"));
            NativeMemory.memPutInt(BrowserStbWrite.compressionLevelAddress(), 9);
            write("level9.png", width, height, comp, data, 0);
            assertTrue(Files.size(dir.resolve("level9.png")) < storedSize, "level 9 smaller than stored");
            for (int filter = 0; filter < 5; filter++) {
                NativeMemory.memPutInt(BrowserStbWrite.forcePngFilterAddress(), filter);
                assertPixels(write("f" + filter + ".png", width, height, comp, data, 0), width, height, comp, false);
            }
        } finally { NativeArena.free(data); }
    }

    @Test void failuresReturnZero() {
        long data = image(2, 2, 4, 8);
        long missing = cString(dir.resolve("no/such/dir/x.png").toString());
        try {
            assertEquals(0, BrowserStbWrite.writePng(missing, 2, 2, 4, data, 8));
            assertEquals(0, BrowserStbWrite.writePng(missing, 2, 2, 5, data, 8));
            assertEquals(0, BrowserStbWrite.writePng(missing, 0, 2, 4, data, 8));
        } finally { NativeArena.free(data); NativeArena.free(missing); }
    }
}
