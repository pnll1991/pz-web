package port.compiler;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.teavm.diagnostics.DefaultProblemTextConsumer;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

/** Compiler-side code only; never included in the browser entrypoint. */
public final class BuildCompiler {
    public static void main(String[] args) throws Exception {
        Path port = Path.of(args[0]);
        Path config = port.resolve("target/config");
        Path output = port.resolve("target/web");
        Files.createDirectories(output);
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
        for (String entry : Files.readString(port.resolve("target/dependency-classpath.txt")).strip().split(File.pathSeparator)) {
            classPath.add(Path.of(entry).toFile());
        }
        List<URL> urls = new ArrayList<>();
        for (File file : classPath) urls.add(file.toURI().toURL());
        System.setProperty("pz.nativeIndex", config.resolve("natives.tsv").toString());
        System.setProperty("pz.glIndex", config.resolve("gl-overrides.tsv").toString());
        System.setProperty("pz.reflection", port.resolve("config/reflection.txt").toString());
        long started = System.nanoTime();
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), BuildCompiler.class.getClassLoader())) {
            TeaVMTool tool = new TeaVMTool();
            tool.setClassLoader(loader);
            tool.setClassPath(classPath);
            tool.setMainClass("port.Boot");
            tool.setTargetType(TeaVMTargetType.WEBASSEMBLY_GC);
            tool.setTargetDirectory(output.toFile());
            tool.setTargetFileName("boot.wasm");
            tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
            tool.setObfuscated(false);
            tool.setLog(new ConsoleTeaVMToolLog(true));
            tool.getTransformers().add(FailFastNatives.class.getName());
            try {
                tool.generate();
            } catch (Exception error) {
                Files.writeString(port.resolve("target/teavm-diagnostics.txt"), error.toString());
                Files.writeString(port.resolve("target/build-report.json"), "{\"success\":false,\"mode\":\"platform-smoke\",\"game_initialized\":false}\n");
                throw error;
            }
            StringBuilder problems = new StringBuilder();
            for (var problem : tool.getProblemProvider().getProblems()) {
                var text = new DefaultProblemTextConsumer();
                problem.render(text);
                problems.append(problem.getSeverity()).append(": ").append(problem.getLocation() == null ? "compiler" : problem.getLocation().getMethod())
                    .append(": ").append(text.getText()).append('\n');
            }
            Files.writeString(port.resolve("target/teavm-diagnostics.txt"), problems);
            StringBuilder jdkProblems = new StringBuilder("Scope: reachable platform code only; the game has not been initialized.\n");
            for (String line : problems.toString().split("\n")) {
                if (line.contains("java.") || line.contains("javax.") || line.contains("jdk.") || line.contains("sun.")) {
                    jdkProblems.append(line).append('\n');
                }
            }
            Files.writeString(port.resolve("target/jdk-api-report.txt"), jdkProblems);
            boolean success = tool.getProblemProvider().getSevereProblems().isEmpty();
            long bytes = success && Files.exists(output.resolve("boot.wasm")) ? Files.size(output.resolve("boot.wasm")) : 0;
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            var dependencies = tool.getDependencyInfo();
            String report = """
                    {"mode":"platform-smoke","game_initialized":false,"success":%s,
                     "teavm":"0.16.0","target":"WEBASSEMBLY_GC",
                     "reachable_classes":%d,"reachable_methods":%d,
                     "wasm_bytes":%d,"compile_ms":%d,"external_classpath_entries":%d,
                     "diagnostics_file":"teavm-diagnostics.txt"}
                    """.formatted(success, dependencies.getReachableClasses().size(),
                    dependencies.getReachableMethods().size(), bytes, elapsed, externalCount);
            Files.writeString(port.resolve("target/build-report.json"), report);
            System.out.println(report);
            if (!success) throw new IllegalStateException("TeaVM compilation failed:\n" + problems);
        }
    }
}
