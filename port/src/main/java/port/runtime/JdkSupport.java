package port.runtime;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/** Portable API operations missing from the pinned TeaVM class library. */
public final class JdkSupport {
    public static Path toPath(File file) { return Paths.get(file.getPath()); }

    public static Path resolve(Path path, String first, String[] more) {
        Objects.requireNonNull(more);
        Path result = path.resolve(first);
        for (String part : more) result = result.resolve(part);
        return result;
    }

    public static long transferTo(Reader reader, Writer writer) throws IOException {
        Objects.requireNonNull(writer);
        char[] buffer = new char[8192];
        long total = 0;
        int count;
        while ((count = reader.read(buffer, 0, buffer.length)) != -1) {
            writer.write(buffer, 0, count);
            total += count;
        }
        return total;
    }

    /**
     * Cooperative yield for the browser backend.
     *
     * TeaVM 0.16.0 implements threads as coroutines: every suspension point replays the fiber's
     * pending-call chain on the JavaScript stack, so a Java loop that suspends on each iteration
     * (zombie.core.opengl.RenderThread#renderLoop calls Thread.yield) permanently keeps ~3 JS
     * frames per suspension until V8 throws RangeError: Maximum call stack size exceeded. That
     * was verified in round 28: the overflow stack of boot.wasm contains 3370 cycles of
     * Fiber::reversePush/Fiber::push/Arrays::copyOf under Thread::switchContext <- Thread::yield.
     *
     * Yielding on every call therefore kills the boot, and not yielding at all starves the other
     * fibers. This suspends only once per YIELD_GRANT calls, which bounds the replay depth while
     * still handing control to the browser event loop (and to the other fibers) regularly. It is
     * a rate limit, not a fix: the real fix is a scheduler that does not replay pending calls.
     */
    private static final int YIELD_GRANT = 64;
    private static long yieldCalls;
    public static long yieldCount() { return yieldCalls; }

    /**
     * Round 32: true while a render-context item is waiting for the render thread, i.e. while the
     * game fiber is parked in RenderContextQueueItem#waitUntilFinished -> Object#wait. The render
     * fiber must not suspend in that window: TeaVM 0.16.0 keeps a single static
     * `Fiber.lastPendingCall`, and a parked game fiber plus a suspending render fiber produces the
     * `RuntimeError: array element access out of bounds` at Fiber::popInt that rounds 31/32 measured.
     * The flag is written only by observer injections (StartupProgress), never by hand.
     */
    // Round 32: the flag now lives in JS (globalThis.pzRenderItemPending), written by the observer
    // injections and read by BrowserYield.suspendRequested(), so the yield decision is one JS read.


    /** `yield` is a restricted identifier: the only legal call is the qualified JdkSupport.yield(). */
    public static void yield() {
        yieldCalls++;
        if (yieldCalls % YIELD_GRANT != 0) return;
        if (!port.shim.BrowserYield.suspendRequested()) return;
        try {
            // Round 31: Thread.sleep(0L) suspends the fiber but TeaVM 0.16.0 registers no timer for
            // it on WasmGC (the page creates exactly one setTimeout: the renderThread start), so the
            // suspended render fiber was never resumed and the game thread stayed parked on
            // RenderContextQueueItem.waitLock. BrowserYield.pause suspends on a real macrotask, which
            // the browser event loop does fire.
            port.shim.BrowserYield.pause();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    public static void onSpinWait() { JdkSupport.yield(); }

    public static int read(InputStream stream) throws IOException {
        byte[] one = new byte[1];
        return stream.read(one, 0, 1) < 0 ? -1 : one[0] & 255;
    }

    public static float fma(float a, float b, float c) { return FusedMath.fma(a, b, c); }
    public static double fma(double a, double b, double c) { return FusedMath.fma(a, b, c); }
    public static String replaceAll(Matcher matcher, Function<MatchResult, String> replacement) {
        Objects.requireNonNull(replacement); matcher.reset(); var result = new StringBuffer();
        while (matcher.find()) {
            var match = matcher.toMatchResult(); String text = replacement.apply(match);
            try {
                if (matcher.start() != match.start() || matcher.end() != match.end() || !matcher.group().equals(match.group()))
                    throw new java.util.ConcurrentModificationException("Replacement function changed its Matcher");
            } catch (IllegalStateException error) { throw new java.util.ConcurrentModificationException("Replacement function reset its Matcher"); }
            matcher.appendReplacement(result, text);
        }
        matcher.appendTail(result); return result.toString();
    }
    public static void update(CRC32 checksum, ByteBuffer buffer) {
        while (buffer.hasRemaining()) checksum.update(buffer.get() & 255);
    }
    public static java.time.Instant toInstant(java.util.GregorianCalendar calendar) {
        return java.time.Instant.ofEpochMilli(calendar.getTimeInMillis());
    }
    public static java.util.stream.Stream<?> parallelStream(java.util.Collection<?> collection) {
        // Parallel execution is optional; the browser's Java heap has one executor thread.
        return collection.stream();
    }
    public static Map<String, String> getenv() { return Collections.emptyMap(); }
    public static String mapLibraryName(String name) { return "lib" + Objects.requireNonNull(name) + ".so"; }
    public static void load(String filename) { throw new UnsatisfiedLinkError("System.load: native ELF cannot be loaded in WebAssembly GC: " + filename); }
    // A library name is satisfied only when the runtime registered a module that honestly provides it.
    public static void loadLibrary(String library) {
        Objects.requireNonNull(library);
        if (libraryRegistered(library)) return;
        recordMissingLibrary(library);
        throw new UnsatisfiedLinkError("System.loadLibrary: browser implementation is missing for " + library);
    }
    @org.teavm.jso.JSBody(params="library",script="var names=globalThis.pzRuntime&&globalThis.pzRuntime.loadedLibraries;return !!names&&names.indexOf(library)>=0;")
    private static native boolean libraryRegistered(String library);
    /**
     * System.exit cannot be honoured in a tab, so it is recorded and then refused with its own
     * identity. Recording matters: the game calls exit(1) from zombie.iso.LightingJNI.init when
     * loadLibrary("Lighting64") fails, and exit(0) at the end of RenderThread.renderLoop once
     * GameWindow.gameThreadExited is set. Without the record the browser only sees an opaque
     * RuntimeException and the next round cannot tell which native is missing.
     */
    public static void exit(int status) {
        recordExit(status);
        throw new UnsupportedOperationException("System.exit(" + status + "): a browser tab is not an OS process");
    }
    @org.teavm.jso.JSBody(params="library",script="var r=globalThis.pzRuntime;if(!r.missingLibraries)r.missingLibraries=[];if(r.missingLibraries.indexOf(library)<0)r.missingLibraries.push(library);")
    private static native void recordMissingLibrary(String library);
    @org.teavm.jso.JSBody(params="status",script="var r=globalThis.pzRuntime;var w=r.window;if(!r.exits)r.exits=[];r.exits.push({status:status,at:Math.round(performance.now()),swapCount:w?w.swapCount:null,windowCreated:w?!!w.created:null,contextCurrent:w?!!w.contextCurrent:null});")
    private static native void recordExit(int status);
    /**
     * TeaVM's java.text.NumberFormat constructor resolved its currency eagerly through
     * Currency.getInstance(Locale.getDefault()); that classlib call throws for a country-less locale
     * because TeaVM's baked ISO-4217 table lacks the country its CLDR metadata resolves (real JDK 25
     * measured with /tmp/CurrCheck.java: DecimalFormatSymbols.getInstance(Locale.ENGLISH).getCurrency()
     * is XXX and no exception is raised). This bridge keeps the real currency when the browser's table
     * has it and answers the JDK's currency-less XXX otherwise, recording each fallback so the
     * deviation is visible in the Chrome diagnostics instead of silently swallowed.
     */
    public static java.util.Currency currencyForLocale(java.util.Locale locale) {
        Objects.requireNonNull(locale);
        try {
            var currency = java.util.Currency.getInstance(locale);
            if (currency != null) return currency;
            recordCurrencyFallback(locale.toString(), "no country-to-currency entry");
            return java.util.Currency.getInstance("XXX");
        } catch (IllegalArgumentException missing) {
            recordCurrencyFallback(locale.toString(), missing.getMessage());
            return java.util.Currency.getInstance("XXX");
        }
    }
    @org.teavm.jso.JSBody(params={"locale","reason"}, script="var r=globalThis.pzRuntime;if(!r.currencyFallbacks)r.currencyFallbacks=[];if(r.currencyFallbacks.indexOf(locale+' -> XXX ('+reason+')')<0)r.currencyFallbacks.push(locale+' -> XXX ('+reason+')');")
    private static native void recordCurrencyFallback(String locale, String reason);

    public static void addShutdownHook(Runtime runtime, Thread thread) {
        Objects.requireNonNull(thread);
        throw new UnsupportedOperationException("Runtime.addShutdownHook: browser unload cannot guarantee Java execution; use explicit save/close");
    }
    public static long maxMemory(Runtime runtime) { return (long) heapLimit(); }
    @org.teavm.jso.JSBody(script="if(!performance.memory)throw Error('Runtime.maxMemory: Chrome heap limit unavailable');return performance.memory.jsHeapSizeLimit;")
    private static native double heapLimit();
    public static long getTotalSpace(File file) { return file.exists() ? (long) vfsCapacity() : 0; }
    public static long getFreeSpace(File file) { return file.exists() ? (long) vfsAvailable() : 0; }
    public static long getUsableSpace(File file) { return file.canWrite() ? getFreeSpace(file) : 0; }
    @org.teavm.jso.JSBody(script="return Math.max(0,pzRuntime.vfs.maxBytes-pzRuntime.vfs.byteLength);")
    private static native double vfsAvailable();
    @org.teavm.jso.JSBody(script="return pzRuntime.vfs.maxBytes;")
    private static native double vfsCapacity();
    public static boolean setReadable(File file, boolean readable, boolean ownerOnly) {
        // The preloaded VFS has no per-user read ACL. Refuse an unsupported change.
        return readable && file.canRead();
    }
    public static Class<?> loadClass(ClassLoader loader, String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
    public static boolean not(boolean value) { return !value; }
    public static void join(Thread thread, long millis, int nanos, Object completion) throws InterruptedException {
        if (millis < 0 || nanos < 0 || nanos > 999999) throw new IllegalArgumentException("Invalid join timeout");
        boolean timed = millis != 0 || nanos != 0;
        long limit = millis > (Long.MAX_VALUE - nanos) / 1_000_000 ? Long.MAX_VALUE : millis * 1_000_000 + nanos;
        long start = System.nanoTime();
        synchronized (completion) {
            while (thread.isAlive()) {
                if (!timed) completion.wait();
                else {
                    long remaining = limit - (System.nanoTime() - start);
                    if (remaining <= 0) break;
                    java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(completion,remaining);
                }
            }
        }
    }
    private static IOException noProcess() { return new IOException("Runtime.exec: OS process execution is unavailable in the browser"); }
    public static Process exec(Runtime runtime, String command) throws IOException { throw noProcess(); }
    public static Process exec(Runtime runtime, String[] command) throws IOException { throw noProcess(); }
    public static Process exec(Runtime runtime, String command, String[] environment) throws IOException { throw noProcess(); }
    public static Process exec(Runtime runtime, String[] command, String[] environment) throws IOException { throw noProcess(); }
    public static Process exec(Runtime runtime, String command, String[] environment, File directory) throws IOException { throw noProcess(); }
    public static Process exec(Runtime runtime, String[] command, String[] environment, File directory) throws IOException { throw noProcess(); }
    public static String uuidValue(long most, long least) {
        String high = Long.toHexString(most), low = Long.toHexString(least);
        high = "0".repeat(16-high.length()) + high; low = "0".repeat(16-low.length()) + low;
        return high.substring(0,8) + "-" + high.substring(8,12) + "-" + high.substring(12) + "-" + low.substring(0,4) + "-" + low.substring(4);
    }
    public static long getMostSignificantBits(java.util.UUID uuid) { return hexLong(uuid.toString().replace("-", "").substring(0,16)); }
    public static long getLeastSignificantBits(java.util.UUID uuid) { return hexLong(uuid.toString().replace("-", "").substring(16)); }
    private static long hexLong(String text) {
        if (text.length() != 16) throw new IllegalArgumentException("UUID half must have 16 hex digits");
        long value = 0;
        for (int i = 0; i < 16; i++) {
            int digit = Character.digit(text.charAt(i),16);
            if (digit < 0) throw new IllegalArgumentException("Invalid UUID hex digit");
            value = value << 4 | digit;
        }
        return value;
    }
    public static char[] highBytes(byte[] bytes, int high, int offset, int count) {
        Objects.checkFromIndexSize(offset, count, bytes.length); char[] chars = new char[count];
        for (int i = 0; i < count; i++) chars[i] = (char) ((high & 255) << 8 | bytes[offset + i] & 255);
        return chars;
    }
}
