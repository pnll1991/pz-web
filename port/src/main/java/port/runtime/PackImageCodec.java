package port.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Ronda 81 (frente 2): estado de un {@code zombie.core.textures.ImageData} recién cargado de una página
 * de .pack ({@code FileTask_LoadPackImage.call}: PNG decodificado, máscara y, con el flag 64, mipmaps y
 * alfa premultiplicado), como metadatos + bytes de cada nivel, y su reconstrucción.
 *
 * Captura TODO el estado del objeto (los campos de instancia de ImageData según javap del jar fijado,
 * más MipMapLevel, WrappedBuffer, el ByteBuffer con su position/limit y BooleanGrid) y conserva qué
 * campos apuntan al mismo nivel. Si la forma no es la cubierta (frames de APNG, buffers liberados,
 * grillas inconsistentes) {@link #capture} devuelve null y la caché no guarda nada. Los niveles se
 * reservan con el mismo {@code new MipMapLevel(w, h)} del juego (DirectBufferAllocator), así que la
 * memoria contada por el juego es la misma. PackImageCacheTest compara campo por campo en la JVM.
 */
public final class PackImageCodec {
    /** Cambiar cuando cambie el formato o lo que se captura (es parte de la clave de la caché). */
    public static final int FORMAT = 1;
    private static final int MAGIC = 0x50494331; // "PIC1"

    private PackImageCodec() { }

    /** Metadatos (little-endian) y los buffers vivos de cada nivel distinto, en orden de índice. */
    public static final class Captured {
        public final ByteBuffer meta;
        public final ByteBuffer[] levels;
        Captured(ByteBuffer meta, ByteBuffer[] levels) { this.meta = meta; this.levels = levels; }
    }

    /** Llena el buffer de un nivel recién reservado (position 0, limit = capacity) con sus bytes guardados. */
    public interface LevelFiller { void fill(int level, ByteBuffer target); }

    public static Captured capture(Object image) {
        if (image == null || PackImageObjects.imageFrameCount(image) != 0) return null;
        List<Object> distinct = new ArrayList<>();
        IdentityHashMap<Object, Integer> index = new IdentityHashMap<>();
        Object data = PackImageObjects.imageData(image);
        Object[] mips = PackImageObjects.imageMipMaps(image);
        int dataIndex = indexOf(data, distinct, index);
        int[] mipIndex = null;
        if (mips != null) {
            mipIndex = new int[mips.length];
            for (int i = 0; i < mips.length; i++) mipIndex[i] = indexOf(mips[i], distinct, index);
        }
        Object grid = PackImageObjects.imageMask(image);
        int[] gridValue = null;
        int gridWidth = 0, gridHeight = 0;
        if (grid != null) {
            // BooleanGrid(w, h) (javap): bitWidth = w, width = ceil(w / 32) palabras, value = new int[width * h].
            // Se guarda el argumento del constructor (bitWidth) y se exige que lo derivado coincida.
            gridWidth = PackImageObjects.gridBitWidth(grid); gridHeight = PackImageObjects.gridHeight(grid);
            gridValue = PackImageObjects.gridValue(grid);
            int words = gridWidth / 32 + (gridWidth % 32 != 0 ? 1 : 0);
            if (gridValue == null || PackImageObjects.gridWidth(grid) != words || gridValue.length != words * gridHeight) return null;
        }
        int ints = 16 + (mipIndex == null ? 0 : mipIndex.length) + distinct.size() * 5 + 3 + (gridValue == null ? 0 : gridValue.length);
        ByteBuffer meta = ByteBuffer.allocate(ints * 4).order(ByteOrder.LITTLE_ENDIAN);
        meta.putInt(MAGIC).putInt(FORMAT);
        meta.putInt(PackImageObjects.imageWidth(image)).putInt(PackImageObjects.imageHeight(image));
        meta.putInt(PackImageObjects.imageWidthHw(image)).putInt(PackImageObjects.imageHeightHw(image));
        meta.putInt(PackImageObjects.imageMipMapCount(image)).putInt(PackImageObjects.imageId(image));
        meta.putInt(bits(PackImageObjects.imageSolid(image), PackImageObjects.imageAlphaPaddingDone(image),
            PackImageObjects.imagePreMultipliedAlphaDone(image), PackImageObjects.imagePreserveTransparentColor(image)));
        meta.putInt(dataIndex);
        meta.putInt(mipIndex == null ? -1 : mipIndex.length);
        if (mipIndex != null) for (int i : mipIndex) meta.putInt(i);
        meta.putInt(distinct.size());
        ByteBuffer[] buffers = new ByteBuffer[distinct.size()];
        for (int i = 0; i < buffers.length; i++) {
            Object level = distinct.get(i);
            Object wrapped = PackImageObjects.mipWrapped(level);
            if (wrapped == null || PackImageObjects.wrappedDisposed(wrapped)) return null;
            ByteBuffer buffer = PackImageObjects.wrappedBuffer(wrapped);
            int width = PackImageObjects.mipWidth(level), height = PackImageObjects.mipHeight(level);
            // new MipMapLevel(w, h) reserva exactamente w*h*4; otra capacidad no se puede reproducir así.
            if (buffer == null || PackImageObjects.wrappedCapacity(wrapped) != width * height * 4 || buffer.capacity() != width * height * 4) return null;
            meta.putInt(width).putInt(height).putInt(buffer.position()).putInt(buffer.limit()).putInt(buffer.capacity());
            buffers[i] = buffer;
        }
        meta.putInt(grid == null ? 0 : 1).putInt(gridWidth).putInt(gridHeight);
        meta.putInt(gridValue == null ? -1 : gridValue.length);
        if (gridValue != null) for (int v : gridValue) meta.putInt(v);
        meta.flip();
        return new Captured(meta, buffers);
    }

    private static int indexOf(Object level, List<Object> distinct, IdentityHashMap<Object, Integer> index) {
        if (level == null) return -1;
        Integer found = index.get(level);
        if (found != null) return found;
        index.put(level, distinct.size()); distinct.add(level);
        return distinct.size() - 1;
    }
    private static int bits(boolean... flags) { int v = 0; for (int i = 0; i < flags.length; i++) if (flags[i]) v |= 1 << i; return v; }

    /** Reconstruye el ImageData; lanza IllegalStateException si los metadatos no son de este formato. */
    public static Object restore(ByteBuffer meta, LevelFiller filler) {
        meta = meta.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (meta.getInt() != MAGIC || meta.getInt() != FORMAT) throw new IllegalStateException("PackImageCodec: formato distinto");
        int width = meta.getInt(), height = meta.getInt(), widthHw = meta.getInt(), heightHw = meta.getInt();
        int mipMapCount = meta.getInt(), id = meta.getInt(), flags = meta.getInt(), dataIndex = meta.getInt();
        int mipLength = meta.getInt();
        int[] mipIndex = null;
        if (mipLength >= 0) { mipIndex = new int[mipLength]; for (int i = 0; i < mipLength; i++) mipIndex[i] = meta.getInt(); }
        Object[] levels = new Object[meta.getInt()];
        for (int i = 0; i < levels.length; i++) {
            int w = meta.getInt(), h = meta.getInt(), position = meta.getInt(), limit = meta.getInt(), capacity = meta.getInt();
            Object level = PackImageObjects.newMip(w, h);
            ByteBuffer buffer = PackImageObjects.wrappedBuffer(PackImageObjects.mipWrapped(level));
            if (buffer.capacity() != capacity) throw new IllegalStateException("PackImageCodec: capacidad " + buffer.capacity() + " != " + capacity);
            buffer.limit(capacity).position(0);
            filler.fill(i, buffer);
            buffer.limit(limit).position(position);
            levels[i] = level;
        }
        boolean hasGrid = meta.getInt() != 0;
        int gridWidth = meta.getInt(), gridHeight = meta.getInt(), gridLength = meta.getInt();
        Object grid = null;
        if (hasGrid) {
            grid = PackImageObjects.newGrid(gridWidth, gridHeight);
            int[] value = PackImageObjects.gridValue(grid);
            if (value.length != gridLength) throw new IllegalStateException("PackImageCodec: grilla " + value.length + " != " + gridLength);
            for (int i = 0; i < gridLength; i++) value[i] = meta.getInt();
        }
        if (meta.hasRemaining()) throw new IllegalStateException("PackImageCodec: metadatos sobrantes");
        Object data = dataIndex < 0 ? null : levels[dataIndex];
        // ImageData(w, h, WrappedBuffer) inicializa frames, solid, id y mipMapCount como el constructor del
        // PNG; su MipMapLevel interno se reemplaza enseguida por el nivel reconstruido (mismo objeto que
        // mipMaps[i] cuando el original los compartía).
        Object image = PackImageObjects.newImage(width, height, data == null ? null : PackImageObjects.mipWrapped(data));
        PackImageObjects.setImageWidth(image, width); PackImageObjects.setImageHeight(image, height);
        PackImageObjects.setImageWidthHw(image, widthHw); PackImageObjects.setImageHeightHw(image, heightHw);
        PackImageObjects.setImageMipMapCount(image, mipMapCount); PackImageObjects.setImageId(image, id);
        PackImageObjects.setImageSolid(image, (flags & 1) != 0); PackImageObjects.setImageAlphaPaddingDone(image, (flags & 2) != 0);
        PackImageObjects.setImagePreMultipliedAlphaDone(image, (flags & 4) != 0);
        PackImageObjects.setImagePreserveTransparentColor(image, (flags & 8) != 0);
        PackImageObjects.setImageData(image, data);
        Object[] mips = null;
        if (mipIndex != null) {
            mips = PackImageObjects.newMipArray(mipIndex.length);
            for (int i = 0; i < mipIndex.length; i++) mips[i] = mipIndex[i] < 0 ? null : levels[mipIndex[i]];
        }
        PackImageObjects.setImageMipMaps(image, mips);
        PackImageObjects.setImageMask(image, grid);
        return image;
    }
}
