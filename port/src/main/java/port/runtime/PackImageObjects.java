package port.runtime;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Ronda 81 (frente 2): los accesos a las clases del juego que necesita la caché de páginas de
 * texturas ({@link PackImageCache}), un paso por método.
 *
 * Nuestras fuentes no compilan contra el jar, así que todo tipo del juego es {@code Object}. Dos
 * implementaciones del mismo contrato (como {@code port.natives.JassimpObjects}):
 *  - TeaVM: {@code port.compiler.PackImageCacheCompatibility} reemplaza cada cuerpo por IR directo
 *    (new / getfield / putfield / invoke sobre las clases del jar, resueltos contra el jar; el build
 *    falla si falta un miembro o si un método de acá no tiene reescritura).
 *  - JVM (tests): reflexión con setAccessible sobre las clases del cargador de contexto.
 * Nada de esto cambia lo que el juego calcula: sólo lee y vuelve a armar el mismo estado.
 */
public final class PackImageObjects {
    private PackImageObjects() { }

    // ---- implementación JVM (no se compila a wasm: el transformer reemplaza los cuerpos) ----
    private static ClassLoader loader() {
        ClassLoader l = Thread.currentThread().getContextClassLoader();
        return l != null ? l : PackImageObjects.class.getClassLoader();
    }
    private static Class<?> type(String name) {
        try { return Class.forName(name, true, loader()); }
        catch (ClassNotFoundException e) { throw new IllegalStateException("clase del juego ausente: " + name, e); }
    }
    private static Field field(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new IllegalStateException("campo ausente: " + cls.getName() + "." + name);
    }
    private static Object get(Object target, String name) {
        try { return field(target.getClass(), name).get(target); } catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
    private static void set(Object target, String name, Object value) {
        try { field(target.getClass(), name).set(target, value); } catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
    private static Object make(String cls, Class<?>[] params, Object... args) {
        try { Constructor<?> c = type(cls).getDeclaredConstructor(params); c.setAccessible(true); return c.newInstance(args); }
        catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(cls, e.getCause());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(cls, e); }
    }
    private static Object call(Object target, String name, Class<?>[] params, Object... args) {
        try {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try { Method m = c.getDeclaredMethod(name, params); m.setAccessible(true); return m.invoke(target, args); }
                catch (NoSuchMethodException ignored) { }
            }
            throw new IllegalStateException("método ausente: " + target.getClass().getName() + "." + name);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(name, e.getCause());
        } catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
    static final String IMAGE = "zombie.core.textures.ImageData";
    static final String MIP = "zombie.core.textures.MipMapLevel";
    static final String WRAPPED = "zombie.core.utils.WrappedBuffer";
    static final String GRID = "zombie.core.utils.BooleanGrid";

    // ---- zombie.asset.FileTask_LoadPackImage ----
    /** {@code TextureIDAssetManager.instance.waitFileTask()}: la primera instrucción del call() original. */
    public static void waitFileTask() {
        Object manager = staticField("zombie.core.textures.TextureIDAssetManager", "instance");
        call(manager, "waitFileTask", new Class<?>[0]);
    }
    private static Object staticField(String cls, String name) {
        try { return field(type(cls), name).get(null); } catch (IllegalAccessException e) { throw new IllegalStateException(name, e); }
    }
    /** {@code fileSystem.openStream(fileSystem.getTexturePackDevice(packName), imageName)}, igual que el call() original. */
    public static InputStream openPage(Object task) {
        Object fs = get(task, "fileSystem");
        Object device = call(fs, "getTexturePackDevice", new Class<?>[]{String.class}, get(task, "packName"));
        return (InputStream) call(fs, "openStream", new Class<?>[]{type("zombie.fileSystem.DeviceList"), String.class}, device, get(task, "imageName"));
    }
    public static boolean taskMask(Object task) { return (Boolean) get(task, "mask"); }
    public static int taskFlags(Object task) { return (Integer) get(task, "flags"); }
    public static String taskPack(Object task) { return (String) get(task, "packName"); }
    public static String taskImage(Object task) { return (String) get(task, "imageName"); }
    /** El call() original del juego, intacto (en TeaVM: el método {@code port$uncachedCall} que deja el transformer). */
    public static Object uncached(Object task) { return call(task, "call", new Class<?>[0]); }

    // ---- zombie.core.textures.ImageData ----
    public static int imageWidth(Object img) { return (Integer) get(img, "width"); }
    public static int imageHeight(Object img) { return (Integer) get(img, "height"); }
    public static int imageWidthHw(Object img) { return (Integer) get(img, "widthHw"); }
    public static int imageHeightHw(Object img) { return (Integer) get(img, "heightHw"); }
    public static int imageMipMapCount(Object img) { return (Integer) get(img, "mipMapCount"); }
    public static int imageId(Object img) { return (Integer) get(img, "id"); }
    public static boolean imageSolid(Object img) { return (Boolean) get(img, "solid"); }
    public static boolean imageAlphaPaddingDone(Object img) { return (Boolean) get(img, "alphaPaddingDone"); }
    public static boolean imagePreMultipliedAlphaDone(Object img) { return (Boolean) get(img, "preMultipliedAlphaDone"); }
    public static boolean imagePreserveTransparentColor(Object img) { return (Boolean) get(img, "preserveTransparentColor"); }
    public static Object imageData(Object img) { return get(img, "data"); }
    public static Object[] imageMipMaps(Object img) { return (Object[]) get(img, "mipMaps"); }
    public static Object imageMask(Object img) { return get(img, "mask"); }
    public static int imageFrameCount(Object img) { return ((java.util.List<?>) get(img, "frames")).size(); }

    /** {@code new ImageData(width, height, wrappedBuffer)}: el constructor público que no decodifica nada. */
    public static Object newImage(int width, int height, Object wrapped) {
        return make(IMAGE, new Class<?>[]{int.class, int.class, type(WRAPPED)}, width, height, wrapped);
    }
    public static void setImageWidth(Object img, int v) { set(img, "width", v); }
    public static void setImageHeight(Object img, int v) { set(img, "height", v); }
    public static void setImageWidthHw(Object img, int v) { set(img, "widthHw", v); }
    public static void setImageHeightHw(Object img, int v) { set(img, "heightHw", v); }
    public static void setImageMipMapCount(Object img, int v) { set(img, "mipMapCount", v); }
    public static void setImageId(Object img, int v) { set(img, "id", v); }
    public static void setImageSolid(Object img, boolean v) { set(img, "solid", v); }
    public static void setImageAlphaPaddingDone(Object img, boolean v) { set(img, "alphaPaddingDone", v); }
    public static void setImagePreMultipliedAlphaDone(Object img, boolean v) { set(img, "preMultipliedAlphaDone", v); }
    public static void setImagePreserveTransparentColor(Object img, boolean v) { set(img, "preserveTransparentColor", v); }
    public static void setImageData(Object img, Object level) { set(img, "data", level); }
    public static void setImageMipMaps(Object img, Object[] levels) { set(img, "mipMaps", levels); }
    public static void setImageMask(Object img, Object grid) { set(img, "mask", grid); }

    // ---- zombie.core.textures.MipMapLevel / zombie.core.utils.WrappedBuffer ----
    /** {@code new MipMapLevel(w, h)}: reserva con {@code DirectBufferAllocator.allocate(w*h*4)} como el juego. */
    public static Object newMip(int width, int height) { return make(MIP, new Class<?>[]{int.class, int.class}, width, height); }
    public static Object[] newMipArray(int length) { return (Object[]) java.lang.reflect.Array.newInstance(type(MIP), length); }
    public static int mipWidth(Object level) { return (Integer) get(level, "width"); }
    public static int mipHeight(Object level) { return (Integer) get(level, "height"); }
    public static Object mipWrapped(Object level) { return get(level, "data"); }
    public static ByteBuffer wrappedBuffer(Object wrapped) { return (ByteBuffer) get(wrapped, "buf"); }
    public static int wrappedCapacity(Object wrapped) { return (Integer) get(wrapped, "capacity"); }
    public static boolean wrappedDisposed(Object wrapped) { return (Boolean) get(wrapped, "disposed"); }

    // ---- zombie.core.utils.BooleanGrid ----
    public static Object newGrid(int width, int height) { return make(GRID, new Class<?>[]{int.class, int.class}, width, height); }
    public static int gridWidth(Object grid) { return (Integer) get(grid, "width"); }
    public static int gridHeight(Object grid) { return (Integer) get(grid, "height"); }
    public static int gridBitWidth(Object grid) { return (Integer) get(grid, "bitWidth"); }
    public static int[] gridValue(Object grid) { return (int[]) get(grid, "value"); }
}
