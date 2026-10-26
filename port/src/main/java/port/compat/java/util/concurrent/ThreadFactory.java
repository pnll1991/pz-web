package port.compat.java.util.concurrent;

@FunctionalInterface
public interface ThreadFactory { Thread newThread(Runnable runnable); }
