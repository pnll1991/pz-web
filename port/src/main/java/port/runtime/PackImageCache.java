package port.runtime;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Uint8Array;
import port.runtime.memory.NativeMemory;

/**
 * Ronda 81 (frente 2): caché en el navegador del resultado de {@code zombie.asset.FileTask_LoadPackImage.call()}
 * (decodificar el PNG de una página de .pack + máscara + mipmaps/alfa premultiplicado), que en el perfil del
 * arranque costaba ~47 s de CPU del hilo principal (prof3-boot0: ImageData/PNGDecoder/jzlib).
 *
 * {@code port.compiler.PackImageCacheCompatibility} deja el call() original intacto como
 * {@code port$uncachedCall()} y hace que call() llame a {@link #call(Object)}. Acá:
 *  1. la misma espera del original ({@code waitFileTask}),
 *  2. se leen del mismo stream los bytes exactos del PNG de la página (firma..IEND),
 *  3. clave = SHA-256 de esos bytes + mask + flags + formato del códec; la versión del códec (CODEC_VERSION en runtime/texcache.mjs, no el build) la antepone texcache.mjs,
 *  4. acierto: {@link PackImageCodec#restore} arma el mismo ImageData (test JVM campo por campo);
 *     fallo: corre el call() original sin cambios y guarda su resultado (copia síncrona, escritura async).
 * Con {@code ?texcache=verify} un acierto también corre el original, compara byte a byte y devuelve el
 * original (nunca el de la caché); con {@code ?nocache=1}/{@code ?texcache=0} es exactamente el call() original.
 * La lectura de IndexedDB suspende sólo la corrutina del FileTask (como BrowserPersistence.flush).
 */
public final class PackImageCache {
    // Ronda 89: la caché sobrevive a los builds; si cambia el layout que guarda este código o PackImageCodec.FORMAT,
    // subir también CODEC_VERSION en port/runtime/texcache.mjs (fuente única de la versión que llavea la caché).
    private static final int MAX_PNG_BYTES = 64 << 20;
    private PackImageCache() { }

    public static Object call(Object task) throws Exception {
        int mode = mode();
        if (mode == 0) return PackImageObjects.uncached(task);
        PackImageObjects.waitFileTask();
        byte[] png;
        try (InputStream stream = PackImageObjects.openPage(task)) { png = readPng(stream); }
        catch (IOException | RuntimeException error) { png = null; }
        if (png == null) { note("unparsed"); return PackImageObjects.uncached(task); }
        String salt = (PackImageObjects.taskMask(task) ? "m1" : "m0") + ":f" + PackImageObjects.taskFlags(task) + ":c" + PackImageCodec.FORMAT;
        JSObject entry;
        ByteBuffer scratch = NativeMemory.memAlloc(png.length);
        try {
            scratch.put(png).flip();
            entry = lookup(Uint8Array.fromJavaBuffer(scratch), salt);
        } catch (IOException error) {
            note("lookupError");
            return PackImageObjects.uncached(task);
        } finally { NativeMemory.memFree(scratch); }
        if (isHit(entry)) {
            if (mode == 2) return verify(task, entry);
            Object restored = restore(entry);
            if (restored != null) { note("hit"); return restored; }
            note("restoreError");
        }
        Object image = PackImageObjects.uncached(task);
        store(entry, image);
        return image;
    }

    /** Firma + chunks hasta IEND inclusive: exactamente lo que PNGDecoder consume de la página. */
    static byte[] readPng(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] header = new byte[8];
        in.readFully(header);
        if ((header[0] & 255) != 0x89 || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') return null;
        out.write(header);
        byte[] buffer = new byte[64 << 10];
        while (true) {
            in.readFully(header);
            long length = ((header[0] & 255L) << 24) | ((header[1] & 255) << 16) | ((header[2] & 255) << 8) | (header[3] & 255);
            if (length + 4 + out.size() > MAX_PNG_BYTES) return null;
            out.write(header);
            long left = length + 4; // datos + CRC
            while (left > 0) {
                int n = (int) Math.min(left, buffer.length);
                in.readFully(buffer, 0, n); out.write(buffer, 0, n); left -= n;
            }
            if (header[4] == 'I' && header[5] == 'E' && header[6] == 'N' && header[7] == 'D') return out.toByteArray();
        }
    }

    private static Object restore(JSObject entry) {
        ByteBuffer meta = NativeMemory.memAlloc(metaLength(entry));
        try {
            copyMeta(entry, Uint8Array.fromJavaBuffer(meta));
            return PackImageCodec.restore(meta, (level, target) -> {
                if (!copyLevel(entry, level, Uint8Array.fromJavaBuffer(whole(target))))
                    throw new IllegalStateException("PackImageCache: nivel " + level + " con otro tamaño");
            });
        } catch (RuntimeException error) {
            return null;
        } finally { NativeMemory.memFree(meta); }
    }

    private static void store(JSObject entry, Object image) {
        PackImageCodec.Captured captured;
        try { captured = PackImageCodec.capture(image); } catch (RuntimeException error) { captured = null; }
        if (captured == null) { note("uncovered"); return; }
        ByteBuffer meta = NativeMemory.memAlloc(captured.meta.remaining());
        try {
            meta.put(captured.meta.duplicate()).flip();
            if (!begin(entry, Uint8Array.fromJavaBuffer(meta), captured.levels.length)) return;
        } finally { NativeMemory.memFree(meta); }
        for (int i = 0; i < captured.levels.length; i++) addLevel(entry, i, Uint8Array.fromJavaBuffer(whole(captured.levels[i])));
        commit(entry);
    }

    private static Object verify(Object task, JSObject entry) {
        Object image = PackImageObjects.uncached(task);
        PackImageCodec.Captured captured;
        try { captured = PackImageCodec.capture(image); } catch (RuntimeException error) { captured = null; }
        boolean same = captured != null;
        if (same) {
            ByteBuffer meta = NativeMemory.memAlloc(captured.meta.remaining());
            try {
                meta.put(captured.meta.duplicate()).flip();
                same = sameMeta(entry, Uint8Array.fromJavaBuffer(meta), captured.levels.length);
            } finally { NativeMemory.memFree(meta); }
            for (int i = 0; same && i < captured.levels.length; i++) same = sameLevel(entry, i, Uint8Array.fromJavaBuffer(whole(captured.levels[i])));
        }
        note(same ? "verifyOk" : "verifyBad");
        return image;
    }

    /**
     * Vista nueva 0..capacity sobre la misma memoria de la arena, sin tocar position/limit del buffer del juego
     * (un duplicate() no está registrado en NativeArena y fromJavaBuffer/address lo tratarían como otro bloque).
     */
    private static ByteBuffer whole(ByteBuffer buffer) {
        return NativeMemory.memByteBuffer(port.runtime.memory.NativeArena.address(buffer, 0), buffer.capacity());
    }

    @Async private static native JSObject lookup(Uint8Array png, String salt) throws IOException;
    private static void lookup(Uint8Array png, String salt, AsyncCallback<JSObject> callback) {
        lookupAsync(png, salt, entry -> callback.complete(entry), message -> callback.error(new IOException(message)));
    }
    @JSFunctor private interface Found extends JSObject { void found(JSObject entry); }
    @JSFunctor private interface Failed extends JSObject { void failed(String message); }
    // La vista sobre la memoria lineal se copia ANTES de cualquier await (un memory.grow la invalida).
    @JSBody(params = {"png", "salt", "found", "failed"}, script =
        "var c=globalThis.pzRuntime&&pzRuntime.texCache; if(!c){failed('sin texCache');return;}"
        + " c.lookup(png.slice(), salt).then(function(e){found(e);}, function(e){failed(String(e));});")
    private static native void lookupAsync(Uint8Array png, String salt, Found found, Failed failed);

    @JSBody(script = "var c=globalThis.pzRuntime&&pzRuntime.texCache; return c?c.mode|0:0;")
    private static native int mode();
    @JSBody(params = "kind", script = "var c=globalThis.pzRuntime&&pzRuntime.texCache; if(c)c.note(kind);")
    private static native void note(String kind);
    @JSBody(params = "e", script = "return !!e.hit;")
    private static native boolean isHit(JSObject entry);
    @JSBody(params = "e", script = "return e.meta.length;")
    private static native int metaLength(JSObject entry);
    @JSBody(params = {"e", "view"}, script = "view.set(e.meta);")
    private static native void copyMeta(JSObject entry, Uint8Array view);
    @JSBody(params = {"e", "i", "view"}, script = "var l=e.levels[i]; if(!l||l.length!==view.length)return false; view.set(l); return true;")
    private static native boolean copyLevel(JSObject entry, int level, Uint8Array view);
    @JSBody(params = {"e", "meta", "count"}, script = "return pzRuntime.texCache.begin(e, meta, count);")
    private static native boolean begin(JSObject entry, Uint8Array meta, int count);
    @JSBody(params = {"e", "i", "view"}, script = "pzRuntime.texCache.addLevel(e, i, view);")
    private static native void addLevel(JSObject entry, int level, Uint8Array view);
    @JSBody(params = "e", script = "pzRuntime.texCache.commit(e);")
    private static native void commit(JSObject entry);
    @JSBody(params = {"e", "meta", "count"}, script = "return pzRuntime.texCache.sameMeta(e, meta, count);")
    private static native boolean sameMeta(JSObject entry, Uint8Array meta, int count);
    @JSBody(params = {"e", "i", "view"}, script = "return pzRuntime.texCache.sameLevel(e, i, view);")
    private static native boolean sameLevel(JSObject entry, int level, Uint8Array view);
}
