package port.shim;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Uint8Array;
import port.runtime.memory.NativeMemory;

/**
 * Browser implementation of the two STB operations the game really uses during boot:
 * {@code stbi_load} of a PNG path (through {@code zombie.core.utils.NativeImage.read}) and
 * {@code stbir_resize_uint8_srgb} (through {@code NativeImage.resize}). The host build of
 * {@code org.lwjgl.stb.LibSTB} cannot be loaded in a tab, so {@code StbCompatibility} routes
 * exactly those entry points here and every other STB function keeps its fail-fast JNI guard.
 *
 * Only JDK and this port's own arena are used: the module classpath has no LWJGL classes, so
 * the signatures are the JDK ones the game's wrappers already have. Decoding is a real PNG
 * decoder (8-bit non-interlaced RGB/RGBA); anything else reports stb's own failure reason and
 * returns null instead of pretending an image was decoded.
 */
public final class BrowserStb {
    /** Guard against unreasonable allocations; the game's icons are 16/32/128 px. */
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_PIXEL_BYTES = 16 * 1024 * 1024;
    /** STB's stbir_pixel_layout_channels table, read from the pinned jar's {@code <clinit>}. */
    private static final int[] LAYOUT_CHANNELS = {3, 1, 2, 3, 4, 4, 4, 4, 4, 2, 2, 4, 4, 4, 4, 2, 2};
    private static final byte[] SIGNATURE = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static boolean flipVertical;
    private static String failureReason = "";

    private BrowserStb() { }

    /** {@code nstbi_set_flip_vertically_on_load}: the decoder honours this flag on load. */
    public static void setFlip(int flag) { flipVertical = flag != 0; }

    /** {@code stbi_failure_reason}: the reason of the last failed load, or an empty string. */
    public static String failureReason() { return failureReason; }

    /** {@code stbi_image_free}: pixel buffers always come from this port's arena. */
    public static void stbiImageFree(ByteBuffer image) { NativeMemory.memFree(image); }

    /**
     * {@code STBImage.stbi_load(CharSequence,int[],int[],int[],int)}. Writes the real dimensions
     * into the three one-element arrays and returns RGBA8 pixels in a fresh arena buffer, exactly
     * like stb with {@code req_channels=4}. Returns null and records the failure reason otherwise.
     */
    public static ByteBuffer stbiLoad(CharSequence path, int[] width, int[] height, int[] channels,
                                      int requestedChannels) {
        String wanted = String.valueOf(path);
        Uint8Array file = readOptional(wanted);
        if (file == null) { failure("unknown image file \"" + wanted + "\""); return null; }
        byte[] bytes = new byte[file.getLength()];
        for (int index = 0; index < bytes.length; index++) bytes[index] = (byte)file.get(index);
        try {
            return decodePng(bytes, width, height, channels);
        } catch (RuntimeException | DataFormatException error) {
            failure("Broken PNG image");
            return null;
        }
    }

    /** Upload one GLFWImage entry (width, height, pixel pointer) to the page's tab icon. */
    public static void uploadIcon(long entries, int count, int structSize) {
        for (int index = 0; index < count; index++) {
            long entry = entries + (long)index * structSize;
            // org.lwjgl.glfw.GLFWImage is __struct(member(4),member(4),POINTER_SIZE).
            int width = NativeMemory.memGetInt(entry);
            int height = NativeMemory.memGetInt(entry + 4);
            long pixels = NativeMemory.memGetLong(entry + 8);
            if (width <= 0 || height <= 0 || pixels == 0) continue;
            long bytes = (long)width * height * 4;
            if (bytes > MAX_PIXEL_BYTES) continue;
            byte[] rgba = new byte[(int)bytes];
            NativeMemory.memCopy(pixels, rgba, 0, rgba.length);
            ByteBuffer direct = ByteBuffer.allocateDirect(rgba.length);
            direct.put(rgba).flip();
            setIconNative(Uint8Array.fromJavaBuffer(direct), width, height);
        }
    }

    /**
     * {@code STBImageResize.stbir_resize_uint8_srgb}. The layout enum is mapped to its channel
     * count with STB's own table; sampling is bilinear per channel, which is exact when the
     * destination equals the source (the boot case: 32x32 to 32x32). sRGB is not corrected here;
     * that approximation is recorded in STATUS.md.
     */
    public static ByteBuffer resize(ByteBuffer source, int sourceWidth, int sourceHeight, int sourceStride,
                                    ByteBuffer destination, int destinationWidth, int destinationHeight,
                                    int destinationStride, int layout) {
        if (source == null || destination == null || sourceWidth <= 0 || sourceHeight <= 0
            || destinationWidth <= 0 || destinationHeight <= 0) {
            failure("Invalid resize arguments");
            return null;
        }
        int channels = layout >= 0 && layout < LAYOUT_CHANNELS.length ? LAYOUT_CHANNELS[layout] : 0;
        if (channels <= 0 || channels > 4) { failure("Unsupported pixel layout: " + layout); return null; }
        long sourceBytes = (long)sourceStride * sourceHeight;
        long destinationBytes = (long)destinationStride * destinationHeight;
        if (sourceBytes > MAX_PIXEL_BYTES || destinationBytes > MAX_PIXEL_BYTES) {
            failure("Resize exceeds the pixel budget");
            return null;
        }
        byte[] from = new byte[(int)sourceBytes];
        NativeMemory.memCopy(NativeMemory.memAddress0(source), from, 0, from.length);
        byte[] to = new byte[(int)destinationBytes];
        for (int y = 0; y < destinationHeight; y++) {
            double sourceY = (y + 0.5) * sourceHeight / destinationHeight - 0.5;
            int above = clamp((int)Math.floor(sourceY), sourceHeight - 1);
            int below = clamp((int)Math.ceil(sourceY), sourceHeight - 1);
            double fractionY = sourceY - above;
            if (fractionY < 0) fractionY = 0;
            for (int x = 0; x < destinationWidth; x++) {
                double sourceX = (x + 0.5) * sourceWidth / destinationWidth - 0.5;
                int left = clamp((int)Math.floor(sourceX), sourceWidth - 1);
                int right = clamp((int)Math.ceil(sourceX), sourceWidth - 1);
                double fractionX = sourceX - left;
                if (fractionX < 0) fractionX = 0;
                for (int channel = 0; channel < channels; channel++) {
                    // sourceStride/destinationStride are bytes per pixel (the game passes width * 4).
                    int topLeft = from[above * sourceStride + left * channels + channel] & 0xFF;
                    int topRight = from[above * sourceStride + right * channels + channel] & 0xFF;
                    int bottomLeft = from[below * sourceStride + left * channels + channel] & 0xFF;
                    int bottomRight = from[below * sourceStride + right * channels + channel] & 0xFF;
                    double top = topLeft + (topRight - topLeft) * fractionX;
                    double bottom = bottomLeft + (bottomRight - bottomLeft) * fractionX;
                    int value = (int)Math.round(top + (bottom - top) * fractionY);
                    to[y * destinationStride + x * channels + channel] = (byte)value;
                }
            }
        }
        NativeMemory.memCopy(to, NativeMemory.memAddress0(destination));
        return destination;
    }

    private static int clamp(int value, int maximum) {
        if (value < 0) return 0;
        return value > maximum ? maximum : value;
    }

    private static void failure(String reason) { failureReason = reason; }

    private static ByteBuffer decodePng(byte[] file, int[] width, int[] height, int[] channels)
        throws DataFormatException {
        if (file.length < SIGNATURE.length) { failure("Broken PNG header"); return null; }
        for (int i = 0; i < SIGNATURE.length; i++) if (file[i] != SIGNATURE[i]) { failure("Invalid signature"); return null; }
        int offset = SIGNATURE.length;
        int pngWidth = 0, pngHeight = 0, bitDepth = 0, colorType = 0, interlace = 0;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        while (offset + 12 <= file.length) {
            int length = readInt(file, offset);
            String type = new String(file, offset + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            int data = offset + 8;
            if (length < 0 || data + length + 4 > file.length) { failure("Unexpected end of file"); return null; }
            if ("IHDR".equals(type)) {
                if (length != 13) { failure("Invalid PNG header"); return null; }
                pngWidth = readInt(file, data); pngHeight = readInt(file, data + 4);
                bitDepth = file[data + 8] & 0xFF; colorType = file[data + 9] & 0xFF;
                if (file[data + 10] != 0 || file[data + 11] != 0) { failure("Unsupported PNG compression/filter method"); return null; }
                interlace = file[data + 12] & 0xFF;
            } else if ("IDAT".equals(type)) {
                compressed.write(file, data, length);
            } else if ("IEND".equals(type)) break;
            offset = data + length + 4;
        }
        if (pngWidth <= 0 || pngHeight <= 0 || pngWidth > MAX_DIMENSION || pngHeight > MAX_DIMENSION) {
            failure("Invalid image size"); return null;
        }
        if (bitDepth != 8 || interlace != 0 || (colorType != 2 && colorType != 6)) {
            failure("Invalid PNG image: only 8-bit non-interlaced RGB/RGBA is supported"); return null;
        }
        int pngChannels = colorType == 6 ? 4 : 3;
        int expected = (pngWidth * pngChannels + 1) * pngHeight;
        if ((long)pngWidth * pngHeight * 4 > MAX_PIXEL_BYTES) { failure("Image exceeds the pixel budget"); return null; }
        byte[] raw = inflate(compressed.toByteArray(), expected);
        byte[] rgba = new byte[pngWidth * pngHeight * 4];
        int stride = pngWidth * pngChannels;
        byte[] previous = new byte[stride];
        byte[] row = new byte[stride];
        for (int y = 0; y < pngHeight; y++) {
            int start = y * (stride + 1);
            unfilter(raw[start], raw, start + 1, row, previous, stride, pngChannels);
            int target = (flipVertical ? pngHeight - 1 - y : y) * pngWidth * 4;
            for (int x = 0; x < pngWidth; x++) {
                rgba[target + x * 4] = row[x * pngChannels];
                rgba[target + x * 4 + 1] = row[x * pngChannels + 1];
                rgba[target + x * 4 + 2] = row[x * pngChannels + 2];
                rgba[target + x * 4 + 3] = pngChannels == 4 ? row[x * pngChannels + 3] : (byte)255;
            }
            System.arraycopy(row, 0, previous, 0, stride);
        }
        width[0] = pngWidth; height[0] = pngHeight; channels[0] = 4;
        failureReason = "";
        ByteBuffer pixels = NativeMemory.memAlloc(pngWidth * pngHeight * 4);
        NativeMemory.memCopy(rgba, NativeMemory.memAddress0(pixels));
        return pixels;
    }

    private static byte[] inflate(byte[] compressed, int expected) throws DataFormatException {
        byte[] output = new byte[expected];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            int written = 0;
            while (written < expected) {
                int count = inflater.inflate(output, written, expected - written);
                if (count == 0) break;
                written += count;
            }
            if (written != expected) throw new DataFormatException("Incomplete PNG image data");
        } finally {
            inflater.end();
        }
        return output;
    }

    private static void unfilter(int filter, byte[] source, int start, byte[] row, byte[] previous,
                                 int stride, int channels) {
        switch (filter) {
            case 0 -> { for (int i = 0; i < stride; i++) row[i] = source[start + i]; }
            case 1 -> {
                for (int i = 0; i < stride; i++) {
                    int left = i < channels ? 0 : row[i - channels] & 0xFF;
                    row[i] = (byte)(source[start + i] + left);
                }
            }
            case 2 -> { for (int i = 0; i < stride; i++) row[i] = (byte)(source[start + i] + (previous[i] & 0xFF)); }
            case 3 -> {
                for (int i = 0; i < stride; i++) {
                    int left = i < channels ? 0 : row[i - channels] & 0xFF;
                    row[i] = (byte)(source[start + i] + ((left + (previous[i] & 0xFF)) >>> 1));
                }
            }
            case 4 -> {
                for (int i = 0; i < stride; i++) {
                    int a = i < channels ? 0 : row[i - channels] & 0xFF;
                    int b = previous[i] & 0xFF;
                    int c = i < channels ? 0 : previous[i - channels] & 0xFF;
                    row[i] = (byte)(source[start + i] + paeth(a, b, c));
                }
            }
            default -> throw new IllegalArgumentException("Invalid PNG filter type: " + filter);
        }
    }

    private static int paeth(int a, int b, int c) {
        int predictor = a + b - c;
        int distanceA = Math.abs(predictor - a);
        int distanceB = Math.abs(predictor - b);
        int distanceC = Math.abs(predictor - c);
        return distanceA <= distanceB && distanceA <= distanceC ? a : distanceB <= distanceC ? b : c;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
            | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    // JSBody cannot take a long address, so the pixel copy happens in Java and only the
    // resulting bytes cross into the page. A missing VFS file is reported as null, never as 0 bytes.
    @JSBody(params="path",
        script="var vfs=globalThis.pzRuntime.vfs;return vfs.exists(path)?vfs.readFile(path):null;")
    private static native Uint8Array readOptional(String path);
    @JSBody(params={"bytes","width","height"},
        script="globalThis.pzRuntime.window.setIcon(bytes,width,height);")
    private static native void setIconNative(Uint8Array bytes, int width, int height);
}
