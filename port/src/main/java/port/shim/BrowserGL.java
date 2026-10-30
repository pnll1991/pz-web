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
    /** LWJGL's {@code GLCapabilities.ADDRESS_BUFFER_SIZE}: one 8-byte cell per entrypoint. */
    private static final int ADDRESS_BUFFER_SLOTS = 2236;
    private static long addressBuffer = 0L;
    private static long providerHandle = 0L;

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

    /**
     * The bridge's real framebuffer-object probe: it creates a WebGL2 texture, attaches it to a
     * WebGL2 framebuffer with a DEPTH24_STENCIL8 renderbuffer, clears through it and reads a pixel
     * back. The bridge caches the result and records the WebGL2 status, so nothing here invents a
     * capability: it is 1 only when WebGL2 reported FRAMEBUFFER_COMPLETE and the clear actually
     * rendered.
     */
    @JSBody(script="return globalThis.pzRuntime.gl.framebufferProbe().complete ? 1 : 0;")
    private static native int fboProbeCompleteRaw();

    /** Diagnostic string for the same probe: status name, steps, readback and WebGL2 errors. */
    @JSBody(script="return JSON.stringify(globalThis.pzRuntime.gl.framebufferProbe());")
    private static native String fboProbeReport();

    public static boolean fboProbeComplete() {
        return fboProbeCompleteRaw() != 0;
    }

    public static String fboProbeReportText() {
        return fboProbeReport();
    }

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

    /**
     * The address cell array {@code GLCapabilities} fills from the provider. It is one arena
     * block of 2236 zeroed cells: an entrypoint the WebGL2 context does not expose stays 0, so
     * the corresponding capability is reported as missing instead of being pretended.
     */
    public static long addressBufferAddress() {
        if (addressBuffer == 0L) {
            long address = NativeArena.allocate(ADDRESS_BUFFER_SLOTS * 8L, 8L);
            NativeArena.fill(address, ADDRESS_BUFFER_SLOTS * 8L, (byte) 0);
            addressBuffer = address;
        }
        return addressBuffer;
    }

    /**
     * Handle for the browser library object that installs this provider. LWJGL's
     * {@code Pointer$Default} rejects a zero address when checks are enabled, so the pseudo handle
     * is a real arena cell: it is never dereferenced as a host module, only used as an identity.
     */
    public static long providerHandle() {
        if (providerHandle == 0L) {
            providerHandle = NativeArena.allocate(8L, 8L);
            NativeArena.putLong(providerHandle, 0L);
        }
        return providerHandle;
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

    /**
     * Branch helper for compiler-emitted IR: the Wasm GC backend mis-types a directly emitted
     * long/long equality, so capability probes ask this Java method instead. True only when a real
     * address was handed out for the name.
     */
    public static boolean addressPresent(long address) {
        return address != 0L;
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
