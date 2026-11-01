package port;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import port.runtime.PackImageCodec;

/**
 * Ronda 81 (frente 2): la caché de páginas de .pack reconstruye EXACTAMENTE el ImageData que deja el
 * call() original de FileTask_LoadPackImage (ImageData(stream, mask) + initMipMaps() con el flag 64).
 * Las clases y los PNG son los del jar/los .pack fijados (PZ_HOME), leídos en el momento; nada se copia.
 * La comparación recorre por reflexión TODOS los campos de instancia (así un campo nuevo o no
 * capturado hace fallar el test), los ByteBuffer completos (capacidad, position, limit, orden, bytes)
 * y las identidades compartidas (data == mipMaps[0]).
 */
class PackImageCacheTest {
    private ClassLoader previous;
    private URLClassLoader jar;

    private static Path home() {
        String home = System.getenv("PZ_HOME");
        Assumptions.assumeTrue(home != null, "PZ_HOME no definido");
        Path p = Path.of(home);
        if (!Files.isRegularFile(p.resolve("projectzomboid.jar"))) p = p.resolve("projectzomboid");
        Assumptions.assumeTrue(Files.isRegularFile(p.resolve("projectzomboid.jar")), "projectzomboid.jar ausente");
        return p;
    }

    @BeforeEach void loadJar() throws Exception {
        jar = new URLClassLoader(new URL[]{home().resolve("projectzomboid.jar").toUri().toURL()}, getClass().getClassLoader());
        previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(jar);
    }

    @AfterEach void restore() throws IOException {
        Thread.currentThread().setContextClassLoader(previous);
        jar.close();
    }

    /** Los primeros {@code count} PNG completos (firma..IEND) de un .pack. */
    static List<byte[]> pngs(Path pack, int count) throws IOException {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        List<byte[]> out = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(pack.toFile(), "r")) {
            byte[] window = new byte[(int) Math.min(file.length(), 64L << 20)];
            file.readFully(window);
            for (int at = 0; at + 8 <= window.length && out.size() < count; at++) {
                boolean match = true;
                for (int k = 0; k < 8 && match; k++) match = window[at + k] == sig[k];
                if (!match) continue;
                int p = at + 8;
                while (true) {
                    int len = ((window[p] & 255) << 24) | ((window[p + 1] & 255) << 16) | ((window[p + 2] & 255) << 8) | (window[p + 3] & 255);
                    String type = new String(window, p + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                    p += 12 + len;
                    if (type.equals("IEND")) break;
                }
                out.add(java.util.Arrays.copyOfRange(window, at, p));
                at = p - 1;
            }
        }
        return out;
    }

    Object original(byte[] png, boolean mask, boolean mips) throws Exception {
        Class<?> imageData = Class.forName("zombie.core.textures.ImageData", true, jar);
        Object image = imageData.getConstructor(java.io.InputStream.class, boolean.class).newInstance(new ByteArrayInputStream(png), mask);
        if (mips) imageData.getMethod("initMipMaps").invoke(image);
        return image;
    }

    Object roundTrip(Object image) {
        PackImageCodec.Captured captured = PackImageCodec.capture(image);
        assertNotNull(captured, "la forma de una página de .pack debe estar cubierta");
        byte[][] stored = new byte[captured.levels.length][];
        for (int i = 0; i < stored.length; i++) {
            ByteBuffer all = captured.levels[i].duplicate(); all.clear();
            stored[i] = new byte[all.remaining()]; all.get(stored[i]);
        }
        ByteBuffer meta = ByteBuffer.allocate(captured.meta.remaining()); meta.put(captured.meta.duplicate()).flip();
        return PackImageCodec.restore(meta, (level, target) -> target.put(stored[level]));
    }

    @Test void restoredPagesEqualTheGameDecodeFieldByField() throws Exception {
        Path packs = home().resolve("media/texturepacks");
        List<byte[]> pages = new ArrayList<>(pngs(packs.resolve("UI.pack"), 2));
        pages.addAll(pngs(packs.resolve("Tiles2x.pack"), 1));
        assertEquals(3, pages.size());
        for (byte[] png : pages)
            for (boolean mask : new boolean[]{false, true})
                for (boolean mips : new boolean[]{false, true}) {
                    Object image = original(png, mask, mips);
                    if (mips) assertTrue(port.runtime.PackImageObjects.imageMipMaps(image).length > 1, "initMipMaps generó niveles");
                    if (mask) assertNotNull(port.runtime.PackImageObjects.imageMask(image), "la máscara existe");
                    Object restored = roundTrip(image);
                    deepEquals(image, restored, "ImageData(mask=" + mask + ",mips=" + mips + ")", new IdentityHashMap<>());
                }
    }

    @Test void allocatesThroughTheGameAllocatorLikeTheOriginal() throws Exception {
        Class<?> allocator = Class.forName("zombie.core.utils.DirectBufferAllocator", true, jar);
        java.lang.reflect.Method bytes = allocator.getMethod("getBytesAllocated");
        byte[] png = pngs(home().resolve("media/texturepacks/UI.pack"), 1).get(0);
        long before = (Long) bytes.invoke(null);
        Object image = original(png, true, true);
        long afterOriginal = (Long) bytes.invoke(null);
        roundTrip(image);
        long afterRestore = (Long) bytes.invoke(null);
        assertEquals(afterOriginal - before, afterRestore - afterOriginal, "mismos bytes reservados por DirectBufferAllocator");
    }

    @Test void comparisonDetectsOneChangedPixelByte() throws Exception {
        // Control negativo: la misma ida y vuelta con un byte cambiado en el último nivel debe fallar.
        Object image = original(pngs(home().resolve("media/texturepacks/UI.pack"), 1).get(0), true, true);
        PackImageCodec.Captured captured = PackImageCodec.capture(image);
        byte[][] stored = new byte[captured.levels.length][];
        for (int i = 0; i < stored.length; i++) {
            ByteBuffer all = captured.levels[i].duplicate(); all.clear();
            stored[i] = new byte[all.remaining()]; all.get(stored[i]);
        }
        stored[stored.length - 1][0] ^= 1;
        Object restored = PackImageCodec.restore(captured.meta, (level, target) -> target.put(stored[level]));
        assertThrows(AssertionError.class, () -> deepEquals(image, restored, "ImageData", new IdentityHashMap<>()));
    }

    @Test void restoreRejectsAnotherFormat() {
        ByteBuffer bad = ByteBuffer.allocate(8); bad.putInt(0x50494331).putInt(PackImageCodec.FORMAT + 1).flip();
        assertThrows(IllegalStateException.class, () -> PackImageCodec.restore(bad, (l, t) -> { }));
    }

    static void deepEquals(Object a, Object b, String path, IdentityHashMap<Object, Object> seen) throws Exception {
        if (a == null || b == null) { assertSame(a, b, path); return; }
        assertEquals(a.getClass(), b.getClass(), path + " clase");
        if (seen.containsKey(a)) { assertSame(seen.get(a), b, path + " identidad compartida"); return; }
        seen.put(a, b);
        Class<?> c = a.getClass();
        if (a instanceof ByteBuffer x) {
            ByteBuffer y = (ByteBuffer) b;
            assertEquals(x.capacity(), y.capacity(), path + " capacity");
            assertEquals(x.position(), y.position(), path + " position");
            assertEquals(x.limit(), y.limit(), path + " limit");
            assertEquals(x.order(), y.order(), path + " order");
            assertEquals(x.isDirect(), y.isDirect(), path + " direct");
            ByteBuffer dx = x.duplicate().clear(), dy = y.duplicate().clear();
            assertEquals(0, dx.compareTo(dy), path + " bytes");
            return;
        }
        if (c.isArray()) {
            int n = java.lang.reflect.Array.getLength(a);
            assertEquals(n, java.lang.reflect.Array.getLength(b), path + " length");
            for (int i = 0; i < n; i++) {
                Object x = java.lang.reflect.Array.get(a, i), y = java.lang.reflect.Array.get(b, i);
                if (c.getComponentType().isPrimitive()) assertEquals(x, y, path + "[" + i + "]");
                else deepEquals(x, y, path + "[" + i + "]", seen);
            }
            return;
        }
        if (c == String.class || c == Integer.class || c == Boolean.class || c == Long.class) { assertEquals(a, b, path); return; }
        if (a instanceof java.util.List<?> la) {
            List<?> lb = (List<?>) b;
            assertEquals(la.size(), lb.size(), path + " size");
            for (int i = 0; i < la.size(); i++) deepEquals(la.get(i), lb.get(i), path + "[" + i + "]", seen);
            return;
        }
        assertTrue(c.getName().startsWith("zombie."), path + ": tipo no esperado en el grafo " + c.getName());
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object x = f.get(a), y = f.get(b);
                if (f.getType().isPrimitive()) assertEquals(x, y, path + "." + f.getName());
                else deepEquals(x, y, path + "." + f.getName(), seen);
            }
    }
}
