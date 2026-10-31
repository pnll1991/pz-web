package port.shim;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import port.runtime.memory.NativeArena;
import port.runtime.memory.NativeMemory;

/**
 * Browser implementation of the {@code stb_image_write} natives the game reaches through
 * {@code org.lwjgl.stb.STBImageWrite}: {@code stbi_write_png} (NativeImage.writePng(String) and
 * Texture.saveOnRenderThread; the save thumbnail goes through Core.TakeScreenshot), the
 * flip-on-write flag, and the four global variables that the class initializer maps through
 * {@code nstbi_write_png_compression_level}, {@code nstbi_write_force_png_filter},
 * {@code nstbi_zlib_compress} and {@code nstbi_write_tga_with_rle}.
 *
 * The globals are real cells in this port's arena with stb's defaults (compression 8, force
 * filter -1, RLE 1, no custom zlib compressor); the encoder reads them on every call exactly
 * like stb does. PNG output follows stb's layout (IHDR, one IDAT, IEND; 8-bit, colour type from
 * comp 1..4; per-row filter chosen by stb's minimum sum-of-absolute-values heuristic unless a
 * filter is forced). The zlib stream comes from java.util.zip.Deflater, so bytes differ from
 * stb's own compressor while the decoded pixels are identical. Pure Java (no JSBody): the file
 * is written with FileOutputStream, i.e. through the browser VFS in the tab.
 */
public final class BrowserStbWrite {
    private static final byte[] SIGNATURE = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /** stbiw__png_to_mem: ctype[n] for n = 1..4 (index 0 unused). */
    private static final int[] COLOR_TYPE = {-1, 0, 4, 2, 6};

    private static boolean flipVertical;
    private static long compressionLevel;
    private static long forcePngFilter;
    private static long zlibCompress;
    private static long tgaWithRle;

    private BrowserStbWrite() { }

    /** {@code nstbi_flip_vertically_on_write(int)}. */
    public static void setFlip(int flag) { flipVertical = flag != 0; }

    /** {@code nstbi_write_png_compression_level()}: address of {@code int stbi_write_png_compression_level = 8}. */
    public static long compressionLevelAddress() { globals(); return compressionLevel; }

    /** {@code nstbi_write_force_png_filter()}: address of {@code int stbi_write_force_png_filter = -1}. */
    public static long forcePngFilterAddress() { globals(); return forcePngFilter; }

    /** {@code nstbi_zlib_compress()}: address of the {@code STBIW_ZLIB_COMPRESS} function pointer (NULL). */
    public static long zlibCompressAddress() { globals(); return zlibCompress; }

    /** {@code nstbi_write_tga_with_rle()}: address of {@code int stbi_write_tga_with_rle = 1}. */
    public static long tgaWithRleAddress() { globals(); return tgaWithRle; }

    private static void globals() {
        if (compressionLevel != 0) return;
        compressionLevel = NativeArena.allocate(4, 4);
        NativeMemory.memPutInt(compressionLevel, 8);
        forcePngFilter = NativeArena.allocate(4, 4);
        NativeMemory.memPutInt(forcePngFilter, -1);
        tgaWithRle = NativeArena.allocate(4, 4);
        NativeMemory.memPutInt(tgaWithRle, 1);
        zlibCompress = NativeArena.allocate(8, 8); // zero-filled: NULL, use the built-in compressor
    }

    /**
     * {@code nstbi_write_png(filename, w, h, comp, data, stride_in_bytes)}: 1 on success, 0 when
     * the file cannot be written or the arguments cannot describe an image (stb's return codes).
     */
    public static int writePng(long filename, int width, int height, int comp, long data, int stride) {
        String path = NativeMemory.memUTF8(filename);
        byte[] png = encodePng(data, width, height, comp, stride);
        if (png == null) return 0;
        try (var out = new FileOutputStream(path)) {
            out.write(png);
        } catch (IOException error) {
            return 0;
        }
        return 1;
    }

    /** {@code stbi_write_png_to_mem}: the PNG file bytes, or null for invalid arguments. */
    static byte[] encodePng(long data, int width, int height, int comp, int stride) {
        if (width <= 0 || height <= 0 || comp < 1 || comp > 4 || data == 0 || stride < 0) return null;
        globals();
        if (NativeMemory.memGetLong(zlibCompress) != 0)
            throw new IllegalStateException("STBImageWrite.stbi_zlib_compress points to a C compressor the browser cannot call");
        int rowBytes = Math.multiplyExact(width, comp);
        if (stride == 0) stride = rowBytes;
        int forceFilter = NativeMemory.memGetInt(forcePngFilter);
        if (forceFilter >= 5) forceFilter = -1;
        int level = Math.max(0, Math.min(9, NativeMemory.memGetInt(compressionLevel)));

        byte[] filtered = new byte[Math.multiplyExact(rowBytes + 1, height)];
        byte[] previous = new byte[rowBytes];
        byte[] current = new byte[rowBytes];
        byte[] candidate = new byte[rowBytes];
        byte[] best = new byte[rowBytes];
        for (int y = 0; y < height; y++) {
            int sourceRow = flipVertical ? height - 1 - y : y;
            NativeMemory.memCopy(data + (long)sourceRow * stride, current, 0, rowBytes);
            int chosen;
            if (forceFilter > -1) {
                chosen = forceFilter;
                filterRow(chosen, current, y == 0 ? null : previous, comp, best);
            } else {
                chosen = 0;
                long bestEstimate = Long.MAX_VALUE;
                for (int filter = 0; filter < 5; filter++) {
                    filterRow(filter, current, y == 0 ? null : previous, comp, candidate);
                    long estimate = 0;
                    for (byte value : candidate) estimate += Math.abs((int)value); // abs((signed char)x)
                    if (estimate < bestEstimate) {
                        bestEstimate = estimate;
                        chosen = filter;
                        System.arraycopy(candidate, 0, best, 0, rowBytes);
                    }
                }
            }
            int at = y * (rowBytes + 1);
            filtered[at] = (byte)chosen;
            System.arraycopy(best, 0, filtered, at + 1, rowBytes);
            byte[] swap = previous; previous = current; current = swap;
        }

        var out = new ByteArrayOutputStream(filtered.length / 2 + 64);
        out.write(SIGNATURE, 0, SIGNATURE.length);
        byte[] header = new byte[13];
        putInt(header, 0, width);
        putInt(header, 4, height);
        header[8] = 8;
        header[9] = (byte)COLOR_TYPE[comp];
        chunk(out, "IHDR", header);
        chunk(out, "IDAT", deflate(filtered, level));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** PNG filter types 0..4 with an all-zero prior row for the first scanline (RFC 2083 6.3). */
    private static void filterRow(int filter, byte[] row, byte[] prior, int bpp, byte[] out) {
        for (int i = 0; i < row.length; i++) {
            int x = row[i] & 0xff;
            int a = i >= bpp ? row[i - bpp] & 0xff : 0;
            int b = prior == null ? 0 : prior[i] & 0xff;
            int c = prior == null || i < bpp ? 0 : prior[i - bpp] & 0xff;
            int predicted = switch (filter) {
                case 0 -> 0;
                case 1 -> a;
                case 2 -> b;
                case 3 -> (a + b) >>> 1;
                case 4 -> paeth(a, b, c);
                default -> throw new IllegalArgumentException("PNG filter " + filter);
            };
            out[i] = (byte)(x - predicted);
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? b : c;
    }

    private static byte[] deflate(byte[] input, int level) {
        var deflater = new Deflater(level);
        try {
            deflater.setInput(input);
            deflater.finish();
            var out = new ByteArrayOutputStream(input.length / 2 + 64);
            byte[] buffer = new byte[65536];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] body) {
        byte[] head = new byte[8];
        putInt(head, 0, body.length);
        for (int i = 0; i < 4; i++) head[4 + i] = (byte)type.charAt(i);
        var crc = new CRC32();
        crc.update(head, 4, 4);
        crc.update(body, 0, body.length);
        byte[] tail = new byte[4];
        putInt(tail, 0, (int)crc.getValue());
        out.write(head, 0, 8);
        out.write(body, 0, body.length);
        out.write(tail, 0, 4);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte)(value >>> 24);
        target[offset + 1] = (byte)(value >>> 16);
        target[offset + 2] = (byte)(value >>> 8);
        target[offset + 3] = (byte)value;
    }
}
