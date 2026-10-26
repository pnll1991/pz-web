package port.compat.java.util.concurrent;
import java.util.function.*;
public interface CompletionStage<T> {
    <U> CompletionStage<U> thenApply(Function<? super T,? extends U> fn);
    CompletionStage<Void> thenAccept(Consumer<? super T> fn);CompletionStage<Void> thenRun(Runnable action);
    <U> CompletionStage<U> thenCompose(Function<? super T,? extends CompletionStage<U>> fn);
    <U> CompletionStage<U> handle(BiFunction<? super T,Throwable,? extends U> fn);
    CompletionStage<T> exceptionally(Function<Throwable,? extends T> fn);
    CompletionStage<T> whenComplete(BiConsumer<? super T,? super Throwable> fn);
    CompletableFuture<T> toCompletableFuture();
}
