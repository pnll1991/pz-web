package port.compiler;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.teavm.diagnostics.DefaultProblemTextConsumer;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

/** Compiler-side code only; never included in the browser entrypoint. */
public final class BuildCompiler {
    public static void main(String[] args) throws Throwable {
        Path port = Path.of(args[0]);
        Path config = port.resolve("target/config");
        Properties entrypoint = new Properties();
        try (var reader = Files.newBufferedReader(config.resolve("entrypoint.properties"))) { entrypoint.load(reader); }
        String mode = entrypoint.getProperty("mode");
        boolean game = "game-main".equals(mode);
        long heapLimit = Runtime.getRuntime().maxMemory();
        String gameMain = entrypoint.getProperty("main", "");
        Path output = port.resolve(game ? "target/web/game" : "target/web");
        Path reports = port.resolve("target/reports/" + mode + "-" + entrypoint.getProperty("reflection"));
        Files.createDirectories(reports);
        // Derived diagnostics must never survive a failed/restarted analysis.
        for (String name : List.of("missing-apis.json", "missing-apis.md", "teavm-diagnostics.txt", "jdk-api-report.txt", "chrome-startup.json", "wasm-functions.json")) {
            Files.deleteIfExists(reports.resolve(name));
            Files.deleteIfExists(port.resolve("target").resolve(name));
        }
        write(port, reports, "build-report.json", "{\"success\":false,\"status\":\"running\",\"mode\":\"" + mode + "\"}\n");
        Files.createDirectories(output);
        Files.writeString(output.resolve("build-state.json"), "{\"success\":false,\"status\":\"running\",\"mode\":\"" + mode + "\"}\n");
        // Prevent a failed compile from leaving an old, apparently working module.
        Files.deleteIfExists(output.resolve("boot.wasm"));
        List<File> classPath = new ArrayList<>();
        classPath.add(port.resolve("target/classes").toFile());
        Path external = config.resolve("classpath.txt");
        if (Files.exists(external)) {
            for (String line : Files.readAllLines(external)) {
                if (!line.isBlank()) classPath.add(Path.of(line).toFile());
            }
        }
        int externalCount = classPath.size() - 1;
        PolymorphicCalls.scan(classPath);
        for (String entry : Files.readString(port.resolve("target/dependency-classpath.txt")).strip().split(File.pathSeparator)) {
            classPath.add(Path.of(entry).toFile());
        }
        List<URL> urls = new ArrayList<>();
        for (File file : classPath) urls.add(file.toURI().toURL());
        System.setProperty("pz.nativeIndex", config.resolve("natives.tsv").toString());
        System.setProperty("pz.glIndex", config.resolve("gl-overrides.tsv").toString());
        System.setProperty("pz.reflection", config.resolve("reflection.txt").toString());
        System.setProperty("pz.gameMain", gameMain);
        long started = System.nanoTime();
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), BuildCompiler.class.getClassLoader())) {
            TeaVMTool tool = new TeaVMTool();
            tool.setClassLoader(loader);
            tool.setClassPath(classPath);
            tool.setMainClass(game ? "port.GameBoot" : "port.Boot");
            tool.setTargetType(TeaVMTargetType.WEBASSEMBLY_GC);
            tool.setTargetDirectory(output.toFile());
            tool.setTargetFileName("boot.wasm");
            tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
            tool.setObfuscated(false);
            tool.setLog(new ConsoleTeaVMToolLog(true));
            tool.setProgressListener(new org.teavm.vm.TeaVMProgressListener(){
                private long last;
                public org.teavm.vm.TeaVMProgressFeedback phaseStarted(org.teavm.vm.TeaVMPhase phase,int count){System.out.println("TeaVM phase "+phase+", work units="+count);last=System.nanoTime();return org.teavm.vm.TeaVMProgressFeedback.CONTINUE;}
                public org.teavm.vm.TeaVMProgressFeedback progressReached(int progress){long now=System.nanoTime();if(now-last>30_000_000_000L){System.out.println("TeaVM progress="+progress);last=now;}return org.teavm.vm.TeaVMProgressFeedback.CONTINUE;}
            });
            tool.getTransformers().add(CompatReferences.class.getName());
            tool.getTransformers().add(FailFastNatives.class.getName());
            tool.getTransformers().add(BulletNativeCompatibility.class.getName());
            tool.getTransformers().add(LightingNativeCompatibility.class.getName());
            tool.getTransformers().add(NativeModuleCompatibility.class.getName());
            tool.getTransformers().add(SqliteCompatibility.class.getName());
            tool.getTransformers().add(LwjglPlatformCompatibility.class.getName());
            tool.getTransformers().add(JdkCompatibility.class.getName());
            tool.getTransformers().add(CurrencyCompatibility.class.getName());
            tool.getTransformers().add(StateParamCallerCompatibility.class.getName());
            tool.getTransformers().add(PolymorphicCalls.class.getName());
            tool.getTransformers().add(MemoryCompatibility.class.getName());
            tool.getTransformers().add(BufferUtilsCompatibility.class.getName());
            tool.getTransformers().add(StringUtilsCompatibility.class.getName());
            tool.getTransformers().add(ReflectionVarargsCompatibility.class.getName());
            tool.getTransformers().add(AssertionDisabledCompatibility.class.getName());
            tool.getTransformers().add(WindowCompatibility.class.getName());
            tool.getTransformers().add(GLCompatibility.class.getName());
            tool.getTransformers().add(FBOSupportCompatibility.class.getName());
            tool.getTransformers().add(StbCompatibility.class.getName());
            tool.getTransformers().add(OptionalTelemetry.class.getName());
            if (game) {
                tool.getTransformers().add(GameEntryTransformer.class.getName());
                tool.getTransformers().add(StartupProgress.class.getName());
            }
            try {
                verifyCompilerPatch();
                tool.generate();
            } catch (Throwable error) {
                Files.deleteIfExists(output.resolve("boot.wasm"));
                write(port, reports, "teavm-diagnostics.txt", error.toString());
                String failure = """
                        {"success":false,"status":"compiler-failed","mode":"%s","reflection":"%s",
                         "failure_type":"%s","game_initialized":false,"wasm_bytes":0,
                         "teavm":"0.16.0","compiler_patch":"compact-reflection-caches-v1",
                         "compile_ms":%d,"jvm_heap_limit_bytes":%d,"process_peak_rss_kib":%d}
                        """.formatted(mode, entrypoint.getProperty("reflection"), error.getClass().getName(),
                        (System.nanoTime() - started) / 1_000_000, heapLimit, peakRss());
                write(port, reports, "build-report.json", failure);
                Files.copy(reports.resolve("build-report.json"), output.resolve("build-state.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                throw error;
            }
            StringBuilder problems = new StringBuilder();
            for (var problem : tool.getProblemProvider().getProblems()) {
                var text = new DefaultProblemTextConsumer();
                problem.render(text);
                problems.append(problem.getSeverity()).append(": ").append(problem.getLocation() == null ? "compiler" : problem.getLocation().getMethod())
                    .append(": ").append(text.getText()).append('\n');
            }
            write(port, reports, "teavm-diagnostics.txt", problems.toString());
            // Round 32: the boundary transformer now emits int ids (see StartupProgress). Publish
            // the id -> label table next to the game module so the browser can decode the pulses
            // into pzGameProgress/pzGameTrace/pzGameCounts labels. Emitted even when the module was
            // rejected, so a failed build never leaves a stale map.
            if (game) {
                String pulseMap = StartupProgress.pulseMapJson();
                Files.writeString(reports.resolve("pulse-map.json"), pulseMap);
                Files.writeString(port.resolve("target").resolve("pulse-map.json"), pulseMap);
                Files.writeString(output.resolve("pulse-map.json"), pulseMap);
            }
            StringBuilder jdkProblems = new StringBuilder("Scope: " + mode + "; compilation reachability is not runtime evidence.\n");
            for (String line : problems.toString().split("\n")) {
                if (line.contains("java.") || line.contains("javax.") || line.contains("jdk.") || line.contains("sun.")) {
                    jdkProblems.append(line).append('\n');
                }
            }
            write(port, reports, "jdk-api-report.txt", jdkProblems.toString());
            boolean success = tool.getProblemProvider().getSevereProblems().isEmpty();
            // A backend may write a module and still report severe diagnostics.
            // Such an output is not a usable build artifact.
            if (!success) Files.deleteIfExists(output.resolve("boot.wasm"));
            long bytes = success && Files.exists(output.resolve("boot.wasm")) ? Files.size(output.resolve("boot.wasm")) : 0;
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            var dependencies = tool.getDependencyInfo();
            String report = """
                    {"mode":"%s","game_main":"%s","reflection":"%s","game_initialized":false,"success":%s,
                     "teavm":"0.16.0","compiler_patch":"compact-reflection-caches-v1","target":"WEBASSEMBLY_GC",
                     "reachable_classes":%d,"reachable_methods":%d,
                     "wasm_bytes":%d,"compile_ms":%d,"external_classpath_entries":%d,
                     "jvm_heap_limit_bytes":%d,"process_peak_rss_kib":%d,
                     "diagnostics_file":"teavm-diagnostics.txt"}
                    """.formatted(mode, gameMain, entrypoint.getProperty("reflection"), success, dependencies.getReachableClasses().size(),
                    dependencies.getReachableMethods().size(), bytes, elapsed, externalCount, heapLimit, peakRss());
            write(port, reports, "build-report.json", report);
            Files.writeString(output.resolve("build-state.json"), report);
            System.out.println(report);
            if (!success) throw new IllegalStateException("TeaVM compilation failed:\n" + problems);
        }
    }

    private static void verifyCompilerPatch() throws Exception {
        Class<?> listener = Class.forName("org.teavm.reflection.ReflectionDependencyListener");
        int patched = 0;
        for (var nested : listener.getDeclaredClasses()) {
            for (var field : nested.getDeclaredFields()) {
                if (field.getName().equals("knownTypes") &&
                        field.getType() == org.teavm.dependency.CompactTypeSet.class) patched++;
            }
        }
        if (patched != 3) throw new IllegalStateException("TeaVM memory patch is not active; use scripts/build.sh");
    }

    private static void write(Path port, Path reports, String name, String value) throws Exception {
        Files.writeString(reports.resolve(name), value);
        Files.writeString(port.resolve("target").resolve(name), value);
    }

    private static long peakRss() throws Exception {
        for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
            if (line.startsWith("VmHWM:")) return Long.parseLong(line.split("\\s+")[1]);
        }
        return -1;
    }
}
