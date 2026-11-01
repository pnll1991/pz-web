package port;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.parsing.resource.ResourceProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate for the transformer patches that must be checked against the compiled model, not only against
 * source text. Round 49 committed a StringUtils patch whose descriptors were built with
 * {@code MethodDescriptor.parse("containsWhitespace:(...)Z")}: TeaVM's parser takes {@code name(args)ret}
 * with NO colon, so {@code equals()} against {@code MethodHolder.getDescriptor()} was false, the patch
 * matched nothing, and boot.wasm kept calling {@code java.lang.String::matches} (stack overflow in
 * {@code ReluctantGroupQuantifierSet}). These tests fail loudly if a patch stops matching.
 */
class TransformerPatchTest {
    static final class TestContext implements org.teavm.model.ClassHolderTransformerContext {
        private final org.teavm.model.ClassHierarchy hierarchy;
        TestContext(org.teavm.model.ClassHierarchy hierarchy) { this.hierarchy = hierarchy; }
        public org.teavm.model.ClassHierarchy getHierarchy() { return hierarchy; }
        public org.teavm.diagnostics.Diagnostics getDiagnostics() { return new org.teavm.diagnostics.AccumulationDiagnostics(); }
        public org.teavm.cache.IncrementalDependencyRegistration getIncrementalCache() { return null; }
        public boolean isObfuscated() { return false; }
        public boolean isStrict() { return false; }
        public String getEntryPoint() { return "port.GameBoot"; }
        public void submit(org.teavm.model.ClassHolder cls) { }
    }
    /** The pinned game jar, resolved from the build config so this never hard-codes a machine path. */
    private static File gameJar() {
        Path config = Path.of(System.getProperty("pz.gameJarConfig", "port/target/config/classpath.txt"));
        if (!Files.exists(config)) config = Path.of(System.getProperty("user.dir"), "target/config/classpath.txt");
        List<String> lines;
        try { lines = Files.readAllLines(config); }
        catch (java.io.IOException e) { throw new IllegalStateException("Missing " + config, e); }
        for (String line : lines) if (line.endsWith(".jar")) return new File(line.strip());
        throw new IllegalStateException("No jar entry in " + config);
    }
    private static org.teavm.model.ClassHolderSource source(File jar) {
        List<File> cp = List.of(Path.of(System.getProperty("user.dir"), "target/classes").toFile(), jar);
        return new org.teavm.parsing.ClasspathClassHolderSource(org.teavm.parsing.resource.ResourceProvider.ofClassPath(cp), new org.teavm.model.ReferenceCache(),
            TransformerPatchTest.class.getClassLoader());
    }
    private static org.teavm.model.instructions.InvokeInstruction soleInvoke(ClassHolder cls, String name) {
        for (var method : cls.getMethods()) {
            if (!method.getName().equals(name)) continue;
            org.teavm.model.instructions.InvokeInstruction found = null;
            for (var block : method.getProgram().getBasicBlocks())
                for (var instruction : block)
                    if (instruction instanceof org.teavm.model.instructions.InvokeInstruction invoke) {
                        if (found != null) fail(name + " must delegate with exactly one invoke");
                        found = invoke;
                    }
            assertNotNull(found, name + " was not rewritten (no invoke in its program)");
            return found;
        }
        throw new AssertionError(name + " not found in " + cls.getName());
    }

    @Test void stringUtilsPatchDelegatesToTheBridge() {
        File jar = gameJar();
        var source = source(jar);
        ClassHolder cls = source.get("zombie.util.StringUtils");
        assertNotNull(cls, "zombie.util.StringUtils must be readable from the pinned jar");
        // Before the patch the bodies use the regex machinery.
        var before = cls.getMethod(MethodDescriptor.parse("containsWhitespace(Ljava/lang/String;)Z"));
        assertNotNull(before, "descriptor form must match the jar method");
        assertTrue(before.hasModifier(ElementModifier.STATIC));

        new port.compiler.StringUtilsCompatibility().transformClass(cls, new TestContext(new org.teavm.model.ClassHierarchy(source)));
        var hit = soleInvoke(cls, "containsWhitespace").getMethod();
        assertEquals("port.runtime.text.StringUtilsBridge", hit.getClassName(),
            "containsWhitespace must delegate to the bridge, got " + hit);
        assertEquals("containsWhitespace", hit.getName());
        assertEquals(MethodDescriptor.parse("containsWhitespace(Ljava/lang/String;)Z"), hit.getDescriptor());
        var removed = soleInvoke(cls, "removeWhitespace").getMethod();
        assertEquals("port.runtime.text.StringUtilsBridge", removed.getClassName(),
            "removeWhitespace must delegate to the bridge, got " + removed);
    }

    @Test void totpHmacUsesTheBridgeNotJavaxCrypto() {
        var source = source(gameJar());
        ClassHolder cls = source.get("de.taimos.totp.TOTP");
        assertNotNull(cls, "de.taimos.totp.TOTP must be readable from the pinned jar");
        var before = cls.getMethod(MethodDescriptor.parse("hmac_sha1([B[B)[B"));
        assertNotNull(before);
        assertTrue(invokeNames(before).contains("javax.crypto.Mac::getInstance"), "the original body needs javax.crypto.Mac");
        new port.compiler.TotpCompatibility().transformClass(cls, new TestContext(new org.teavm.model.ClassHierarchy(source)));
        var hit = soleInvoke(cls, "hmac_sha1").getMethod();
        assertEquals("port.runtime.text.HmacSha1Bridge", hit.getClassName());
        assertEquals(MethodDescriptor.parse("hmac_sha1([B[B)[B"), hit.getDescriptor());
    }

    @Test void hmacSha1BridgeMatchesJavaxCrypto() throws Exception {
        java.util.Random random = new java.util.Random(82);
        int[][] sizes = {{20, 8}, {0, 0}, {63, 64}, {64, 65}, {100, 1000}, {16, 8}};
        for (int[] size : sizes) {
            byte[] key = new byte[size[0]], data = new byte[size[1]];
            random.nextBytes(key); random.nextBytes(data);
            var mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.length == 0 ? new byte[1] : key, "RAW"));
            if (key.length == 0) continue; // javax.crypto refuses an empty key; RFC 2104 allows it, covered by the long-key cases
            org.junit.jupiter.api.Assertions.assertArrayEquals(mac.doFinal(data),
                port.runtime.text.HmacSha1Bridge.hmac_sha1(key, data), "key=" + key.length + " data=" + data.length);
        }
    }

    @Test void spriteOverlayVersionIteratesInJdkHashOrder() {
        var source = source(gameJar());
        ClassHolder cls = source.get("zombie.scripting.entity.components.spriteconfig.SpriteOverlayConfigScript");
        assertNotNull(cls);
        var getVersion = cls.getMethod(MethodDescriptor.parse("getVersion(Lzombie/world/scripts/IVersionHash;)V"));
        assertTrue(invokeNames(getVersion).contains("java.util.HashMap::values"), "the original iterates HashMap.values()");
        new port.compiler.HashOrderCompatibility().transformClass(cls, new TestContext(new org.teavm.model.ClassHierarchy(source)));
        assertTrue(invokeNames(getVersion).contains("port.runtime.text.JdkHashOrder::values"));
        assertTrue(!invokeNames(getVersion).contains("java.util.HashMap::values"));
    }

    @Test void jdkHashOrderMatchesRealHashMapIteration() {
        java.util.Random random = new java.util.Random(82);
        for (int round = 0; round < 300; round++) {
            int n = random.nextInt(60);
            var real = new java.util.HashMap<String, Integer>();
            var linked = new java.util.LinkedHashMap<String, Integer>();
            for (int i = 0; i < n; i++) {
                String key = "Style" + random.nextInt(500) + (random.nextBoolean() ? "_x" : "");
                real.put(key, i); linked.put(key, i);
            }
            assertEquals(new java.util.ArrayList<>(real.values()),
                new java.util.ArrayList<>(port.runtime.text.JdkHashOrder.values(linked)), "round " + round + " n=" + n);
        }
    }

    @Test void assertionTransformerDisablesGameAsserts() {
        // Round 50: the MainThread died with java.lang.AssertionError in
        // XuiSkin$EntityUiStyle::Load. javac emits `$assertionsDisabled = !C.desiredAssertionStatus()`
        // in <clinit>; TeaVM's TClass.desiredAssertionStatus() is hardcoded true, so the game's asserts
        // run even though ProjectZomboid64.json never passes -ea. The transformer must leave
        // no call to desiredAssertionStatus anywhere in the touched classes.
        File jar = gameJar();
        var src = source(jar);
        ClassHolder cls = src.get("zombie.scripting.ui.XuiSkin$EntityUiStyle");
        assertNotNull(cls, "XuiSkin$EntityUiStyle must be readable from the pinned jar");
        var clinit = cls.getMethod(MethodDescriptor.parse("<clinit>()V"));
        assertNotNull(clinit);
        assertTrue(invokeNames(clinit).contains("java.lang.Class::desiredAssertionStatus"),
            "the jar's <clinit> must call desiredAssertionStatus before the patch");
        new port.compiler.AssertionDisabledCompatibility().transformClass(cls, new TestContext(new org.teavm.model.ClassHierarchy(src)));
        assertTrue(invokeNames(clinit).isEmpty(), "the patched <clinit> must not call anything: " + invokeNames(clinit));
        // And it must be applied to a class that does not use asserts at all without breaking it.
        ClassHolder plain = src.get("zombie.util.StringUtils");
        int before = plain.getMethods().stream().mapToInt(m -> m.getProgram() == null ? 0 : countInvokes(m)).sum();
        new port.compiler.AssertionDisabledCompatibility().transformClass(plain, new TestContext(new org.teavm.model.ClassHierarchy(src)));
        assertEquals(before, plain.getMethods().stream().mapToInt(m -> m.getProgram() == null ? 0 : countInvokes(m)).sum(),
            "classes without asserts must be untouched");
    }

    private static java.util.List<String> invokeNames(org.teavm.model.MethodHolder method) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (var block : method.getProgram().getBasicBlocks())
            for (var instruction : block)
                if (instruction instanceof org.teavm.model.instructions.InvokeInstruction invoke)
                    names.add(invoke.getMethod().getClassName() + "::" + invoke.getMethod().getName());
        return names;
    }
    private static int countInvokes(org.teavm.model.MethodHolder method) {
        int n = 0;
        for (var block : method.getProgram().getBasicBlocks())
            for (var instruction : block) if (instruction instanceof org.teavm.model.instructions.InvokeInstruction) n++;
        return n;
    }

    @Test void teaVmDescriptorParserHasNoColonForm() {
        // Documents the exact trap: a colon makes parse() keep it inside the name, so equals() fails.
        var good = MethodDescriptor.parse("containsWhitespace(Ljava/lang/String;)Z");
        var bad = MethodDescriptor.parse("containsWhitespace:(Ljava/lang/String;)Z");
        assertNotEquals(good, bad, "the colon form must not be equal to the real descriptor");
        assertEquals("containsWhitespace", good.getName());
        assertNotEquals("containsWhitespace", bad.getName());
    }

    /** True when the program contains a {@code null} produced by a NullConstant and cast to Class[]. */
    private static boolean hasNullClassArrayCast(ClassHolder cls) {
        for (var method : cls.getMethods()) {
            var program = method.getProgram();
            if (program == null) continue;
            java.util.List<org.teavm.model.Variable> nulls = new java.util.ArrayList<>();
            for (var block : program.getBasicBlocks())
                for (var instruction : block) {
                    if (instruction instanceof org.teavm.model.instructions.NullConstantInstruction nullConstant)
                        nulls.add(nullConstant.getReceiver());
                    if (instruction instanceof org.teavm.model.instructions.CastInstruction cast
                            && nulls.contains(cast.getValue())
                            && org.teavm.model.ValueType.arrayOf(org.teavm.model.ValueType.object("java.lang.Class"))
                                .equals(cast.getTargetType()))
                        return true;
                }
        }
        return false;
    }

    @Test void nullVarargsReflectionBecomesAnEmptyArray() {
        // Round 52: ComponentScriptFactory$ScriptConstructor calls
        // ClassReflection.getConstructor(type,(Class[])null) / getDeclaredConstructor(type,(Class[])null).
        // On the desktop that is the no-arg lookup (verified on JDK 25), but TeaVM's TClass compares
        // parameter types with Arrays.equals, which is false against a null array, so the private no-arg
        // constructor that round 51 made reflectable was never found: 215x UiConfigScript + 134x
        // FluidContainerScript + 3x ContextMenuConfigScript + 1x SpriteConfigScript "missing no-arg
        // constructor" in build/real-browser/console-round51.txt.
        File jar = gameJar();
        var src = source(jar);
        ClassHolder cls = src.get("zombie.entity.ComponentScriptFactory$ScriptConstructor");
        assertNotNull(cls, "ScriptConstructor must be readable from the pinned jar");
        assertTrue(hasNullClassArrayCast(cls), "the jar must hand null to the Class[] varargs parameter");
        new port.compiler.ReflectionVarargsCompatibility().transformClass(cls, new TestContext(new org.teavm.model.ClassHierarchy(src)));
        assertFalse(hasNullClassArrayCast(cls), "the patched class must not cast null to Class[]");
        int sites = 0;
        for (var method : cls.getMethods()) {
            var program = method.getProgram();
            if (program == null) continue;
            for (var block : program.getBasicBlocks())
                for (var instruction : block)
                    if (instruction instanceof org.teavm.model.instructions.InvokeInstruction invoke
                            && invoke.getMethod().getClassName().equals("zombie.entity.util.reflect.ClassReflection")) {
                        sites++;
                        var arg = invoke.getArguments().get(1);
                        boolean constructed = false;
                        for (var check : block)
                            if (check instanceof org.teavm.model.instructions.ConstructArrayInstruction array
                                    && array.getReceiver() == arg) constructed = true;
                        assertTrue(constructed, "ClassReflection must receive a constructed Class[]: " + invoke.getMethod());
                    }
        }
        assertEquals(2, sites, "both lookups must still be present");
    }

    @Test void nullVarargsTransformerRejectsUnexpectedShape() {
        // The transformer must fail loudly rather than silently do nothing on a class it does not know.
        var cls = new ClassHolder("zombie.entity.ComponentScriptFactory$ScriptConstructor");
        cls.setParent("java.lang.Object");
        assertThrows(IllegalStateException.class,
            () -> new port.compiler.ReflectionVarargsCompatibility().transformClass(cls,
                new TestContext(new org.teavm.model.ClassHierarchy(source(gameJar())))),
            "0 null-varargs sites must not look like a successful patch");
    }


    /** Cuenta las lecturas del campo `GLCapabilities.<field>` en todos los métodos de la clase. */
    private static int countCapabilityReads(ClassHolder cls, String field) {
        int n = 0;
        for (var method : cls.getMethods()) {
            if (method.getProgram() == null) continue;
            for (var block : method.getProgram().getBasicBlocks())
                for (var instruction : block)
                    if (instruction instanceof org.teavm.model.instructions.GetFieldInstruction get
                        && get.getField().getClassName().equals("org.lwjgl.opengl.GLCapabilities")
                        && get.getField().getFieldName().equals(field)) n++;
        }
        return n;
    }

    /** Ronda 81b: SkyBox, IsoPuddles e IsoWater derivan apiId de GLCapabilities.OpenGL30; con apiId 0 el skybox se dibuja en la pantalla. */
    @Test void fboApiIdGatesUseTheRealFboProbe() {
        var source = source(gameJar());
        var hierarchy = new org.teavm.model.ClassHierarchy(source);
        for (var target : java.util.List.of("zombie.iso.sprite.SkyBox", "zombie.iso.IsoPuddles", "zombie.iso.IsoWater")) {
            ClassHolder cls = source.get(target);
            assertNotNull(cls, target);
            assertTrue(countCapabilityReads(cls, "OpenGL30") > 0, target + " must read OpenGL30 before the patch");
            int arb = countCapabilityReads(cls, "GL_ARB_framebuffer_object"), ext = countCapabilityReads(cls, "GL_EXT_framebuffer_object");
            new port.compiler.FBOSupportCompatibility().transformClass(cls, new TestContext(hierarchy));
            assertEquals(0, countCapabilityReads(cls, "OpenGL30"), target + " still reads GLCapabilities.OpenGL30");
            assertEquals(arb, countCapabilityReads(cls, "GL_ARB_framebuffer_object"), target + ": the ARB gate must stay as the game wrote it");
            assertEquals(ext, countCapabilityReads(cls, "GL_EXT_framebuffer_object"), target + ": the EXT gate must stay as the game wrote it");
            int probes = 0;
            for (var method : cls.getMethods()) probes += method.getProgram() == null ? 0 : countInvokes(method, "port.shim.BrowserGL", "fboProbeComplete");
            assertTrue(probes > 0, target + " does not ask the FBO probe");
        }
        // Control: the original texture gates keep working, and a class without the read still fails the build.
        for (var target : java.util.List.of("zombie.core.textures.TextureFBO", "zombie.core.textures.TextureFBODepth")) {
            ClassHolder cls = source.get(target);
            assertTrue(countCapabilityReads(cls, "OpenGL30") > 0, target);
            new port.compiler.FBOSupportCompatibility().transformClass(cls, new TestContext(hierarchy));
            assertEquals(0, countCapabilityReads(cls, "OpenGL30"), target);
        }
        var empty = new ClassHolder("zombie.iso.sprite.SkyBox"); empty.setParent("java.lang.Object");
        assertThrows(IllegalStateException.class, () -> new port.compiler.FBOSupportCompatibility().transformClass(empty, new TestContext(hierarchy)));
    }

    /** Ronda 80 (frente O): las tres esperas activas de IngameState.exit ceden mientras esperan. */
    @Test void spinWaitsOnThreadIsAliveYield() {
        var source = source(gameJar());
        var hierarchy = new org.teavm.model.ClassHierarchy(source);
        for (var target : java.util.List.of("zombie.iso.WorldStreamer", "zombie.iso.LightingThread",
                "zombie.iso.areas.isoregion.IsoRegionWorker")) {
            ClassHolder cls = source.get(target);
            assertNotNull(cls, target);
            var stop = cls.getMethod(new MethodDescriptor("stop", org.teavm.model.ValueType.VOID));
            assertNotNull(stop, target + ".stop()");
            assertTrue(countInvokes(stop, "java.lang.Thread", "isAlive") > 0, target + " must poll Thread.isAlive before the patch");
            new port.compiler.SpinWaitCompatibility().transformClass(cls, new TestContext(hierarchy));
            assertEquals(0, countInvokes(stop, "java.lang.Thread", "isAlive"), target + " still spins on Thread.isAlive");
            assertTrue(countInvokes(stop, "port.runtime.JdkSupport", "aliveYielding") > 0, target + " not rewritten");
        }
        // Control: an isAlive wait that already sleeps is left alone.
        ClassHolder pm = source.get("zombie.pathfind.PolygonalMap2");
        int before = countInvokes(pm.getMethod(new MethodDescriptor("stop", org.teavm.model.ValueType.VOID)), "port.runtime.JdkSupport", "aliveYielding");
        new port.compiler.SpinWaitCompatibility().transformClass(pm, new TestContext(hierarchy));
        assertEquals(before, countInvokes(pm.getMethod(new MethodDescriptor("stop", org.teavm.model.ValueType.VOID)), "port.runtime.JdkSupport", "aliveYielding"));
        // A target without the expected call fails the build instead of compiling a silent no-op.
        var empty = new ClassHolder("zombie.iso.LightingThread"); empty.setParent("java.lang.Object");
        assertThrows(IllegalStateException.class, () -> new port.compiler.SpinWaitCompatibility().transformClass(empty, new TestContext(hierarchy)));
    }

    @Test void aliveYieldingKeepsIsAliveValue() throws Exception {
        var thread = new Thread(() -> { });
        assertFalse(port.runtime.JdkSupport.aliveYielding(thread));
        thread.start(); thread.join();
        assertFalse(port.runtime.JdkSupport.aliveYielding(thread));
    }

    private static int countInvokes(org.teavm.model.MethodHolder method, String owner, String name) {
        int count = 0;
        for (var block : method.getProgram().getBasicBlocks())
            for (var instruction : block)
                if (instruction instanceof org.teavm.model.instructions.InvokeInstruction invoke
                        && invoke.getMethod().getClassName().equals(owner) && invoke.getMethod().getName().equals(name)) count++;
        return count;
    }
}