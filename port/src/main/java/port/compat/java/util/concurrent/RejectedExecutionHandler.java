package port.compat.java.util.concurrent;
@FunctionalInterface public interface RejectedExecutionHandler {void rejectedExecution(Runnable task,ThreadPoolExecutor executor);}
