package port.natives;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Our browser implementation of the jassimp64 JNI library (jassimp.Jassimp), over a PRECONVERTED
 * cache ("Preconvertir en tu PC y leer el caché", ROADMAP; docs/decisions.md, round 80 front L).
 *
 * JAssImpImporter.Init() installs a loader that calls System.loadLibrary("jassimp64"); the first
 * Jassimp.importFile then reads the native struct sizes and calls the static native
 * aiImportFile(path, rawPostProcessFlags, ioSystem, progressHandler). Here aiImportFile opens
 * {@code path} through the VFS: the pack serves, at the .x path itself, the cache that
 * tools/jassimp/JassimpCache.java produced by running the pinned libjassimp64.so with the same
 * flags. The scene is rebuilt with the jassimp classes' own constructors/allocators
 * (JassimpCacheReader / JassimpObjects), like the native does with JNI.
 *
 * Honest failures: a missing file, a file that is not a cache (a raw .x), a cache made with other
 * flags or a custom AiIOSystem/progress handler raise IOException / UnsupportedOperationException
 * with the JNI name; there is never an empty scene. Forwarded by
 * port.compiler.JassimpNativeCompatibility.
 */
public final class Jassimp64 {
    public static final String LIBRARY = "jassimp64";
    static final String IMPORT_JNI = "Java_jassimp_Jassimp_aiImportFile__Ljava_lang_String_2JLjassimp_AiIOSystem_2Ljassimp_AiProgressHandler_2";
    private static String lastError = "";

    private Jassimp64() { }

    public static void install() {
        NativeRegistry.register(LIBRARY);
        NativeRegistry.publish(LIBRARY, "installed", null);
    }

    public static Object aiImportFile(String path, long postProcess, Object ioSystem, Object progressHandler) throws IOException {
        if (ioSystem != null)
            throw new UnsupportedOperationException(IMPORT_JNI + ": custom AiIOSystem is not implemented in the browser");
        if (progressHandler != null)
            throw new UnsupportedOperationException(IMPORT_JNI + ": AiProgressHandler is not implemented in the browser");
        try (InputStream in = new FileInputStream(path)) {
            Object scene = JassimpCacheReader.read(in, postProcess, path);
            lastError = "";
            NativeRegistry.publish(LIBRARY, "imported", "aiImportFile");
            return scene;
        } catch (IOException error) {
            // aiImportFile returns NULL and the JNI throws IOException(aiGetErrorString()).
            lastError = error.getMessage() == null ? error.toString() : error.getMessage();
            NativeRegistry.publish(LIBRARY, "import-failed", "aiImportFile-failed");
            throw new IOException(lastError, error);
        }
    }

    public static String getErrorString() { return lastError; }
    public static int getVKeysize() { return JassimpCacheReader.NATIVE_SIZES[0]; }
    public static int getQKeysize() { return JassimpCacheReader.NATIVE_SIZES[1]; }
    public static int getV3Dsize() { return JassimpCacheReader.NATIVE_SIZES[2]; }
    public static int getfloatsize() { return JassimpCacheReader.NATIVE_SIZES[3]; }
    public static int getintsize() { return JassimpCacheReader.NATIVE_SIZES[4]; }
    public static int getuintsize() { return JassimpCacheReader.NATIVE_SIZES[5]; }
    public static int getdoublesize() { return JassimpCacheReader.NATIVE_SIZES[6]; }
    public static int getlongsize() { return JassimpCacheReader.NATIVE_SIZES[7]; }
}
