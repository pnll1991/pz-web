package port.shim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.jso.JSBody;
import port.runtime.memory.NativeArena;

/**
 * Real WebGL2 capability discovery. Every fact comes from the page's WebGL2 context: the API
 * version string, the extension list and whether a given entrypoint exists. An entrypoint the
 * context does not expose gets address 0, so {@code Checks}/{@code GLCapabilities} report the
 * capability as missing instead of pretending it works.
 */
public final class BrowserGL {
    /** Stable non-zero handle per supported entrypoint: one arena slot holding the name's hash. */
    private static final Map<String, Long> HANDLES = new HashMap<>();

    private BrowserGL() { }

    /**
     * WebGL2 has no desktop OpenGL version. The page reports "3.0 (WebGL 2.0)": WebGL2 is
     * OpenGL ES 3.0, and docs/decisions.md records that the game's fixed-function/desktop-only
     * paths (OpenGL33+, ARB_debug_output, compressed textures) therefore stay disabled.
     */
    @JSBody(script="return globalThis.pzRuntime.gl.apiVersion();")
    private static native String apiVersion();

    /** Comma-separated list from gl.getSupportedExtensions(); empty when the context has none. */
    @JSBody(script="return globalThis.pzRuntime.gl.extensions().join(',');")
    private static native String extensionNames();

    /** True only when the WebGL2 context really exposes the method behind the GL name. */
    @JSBody(params="name", script="return globalThis.pzRuntime.gl.hasEntrypoint(name) ? 1 : 0;")
    private static native int hasEntrypointRaw(String name);

    public static int versionMajor() {
        int dot = apiVersion().indexOf('.');
        return dot < 1 ? 0 : parse(apiVersion().substring(0, dot));
    }

    public static int versionMinor() {
        String version = apiVersion();
        int dot = version.indexOf('.');
        if (dot < 0 || dot + 1 >= version.length()) return 0;
        return parse(version.substring(dot + 1, dot + 2));
    }

    public static boolean contextCurrent() {
        // A WebGL2 context always exists in this backend; the bridge constructor would have
        // thrown if the canvas refused to provide one.
        return versionMajor() >= 1;
    }

    /**
     * Version tags follow the pinned LWJGL naming ("OpenGL11" .. "OpenGLXY") and extension tags
     * are the context's own names, so a capability is only true when the browser has it.
     */
    public static Set<String> tags() {
        Set<String> tags = new HashSet<>();
        int major = versionMajor();
        int minor = versionMinor();
        for (int i = 1; i <= major; i++) {
            int limit = i == major ? minor : 5;
            for (int j = 0; j <= limit; j++) {
                if (i == 1 && j == 0) continue; // OpenGL 1.0 does not exist
                tags.add("OpenGL" + i + j);
            }
        }
        String names = extensionNames();
        if (!names.isEmpty()) for (String name : names.split(",")) if (!name.isEmpty()) tags.add(name);
        return tags;
    }

    /** Address of a WebGL2 entrypoint, or 0 when the context does not expose it. */
    public static long functionAddress(String name) {
        if (name == null || name.isEmpty()) return 0L;
        Long cached = HANDLES.get(name);
        if (cached != null) return cached;
        if (hasEntrypointRaw(name) == 0) return 0L;
        long slot = NativeArena.allocate(8L, 8L);
        NativeArena.putLong(slot, name.hashCode() & 0x7fffffffL);
        HANDLES.put(name, slot);
        return slot;
    }

    private static int parse(String text) {
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') break;
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
