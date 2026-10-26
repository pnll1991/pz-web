package port.compiler;

import org.teavm.extension.spi.substitution.SubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/** Exercise the 0.16 substitution SPI without inventing a complete LWJGL class. */
public final class PlatformSubstitutions implements SubstitutionPolicy {
    @Override public void contribute(SubstitutionSink sink) {
        sink.substitutePackage("port.demo.lwjgl", "port.shim");
        // Only explicitly implemented classes override the pinned class library.
        var classes = java.util.Set.of(
                "java.util.concurrent.ConcurrentLinkedQueue", "java.util.concurrent.ConcurrentLinkedDeque",
                "java.util.concurrent.CopyOnWriteArraySet", "java.util.concurrent.Future",
                "java.util.concurrent.RunnableFuture", "java.util.concurrent.FutureTask",
                "java.util.concurrent.TimeoutException", "java.util.concurrent.RejectedExecutionException",
                "java.util.concurrent.Executors", "java.util.concurrent.ExecutorService", "java.util.concurrent.ThreadFactory",
                "java.util.concurrent.locks.Lock", "java.util.concurrent.locks.Condition", "java.util.concurrent.locks.ReentrantLock",
                "java.nio.channels.FileChannel",
                "java.nio.channels.FileLock", "java.lang.Process", "java.lang.ProcessBuilder",
                "java.lang.ThreadGroup", "java.lang.IllegalThreadStateException",
                "java.util.concurrent.ConcurrentHashMap$KeySetView", "java.io.ObjectInput", "java.io.ObjectOutput",
                "java.lang.UnsatisfiedLinkError", "java.lang.ExceptionInInitializerError");
        sink.selectClasses(name -> classes.stream().anyMatch(c -> name.equals(c) || name.startsWith(c + "$")))
                .packagePrefix("port.compat");
    }
}
