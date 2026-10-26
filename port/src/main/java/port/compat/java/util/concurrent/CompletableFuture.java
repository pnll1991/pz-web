package port.compat.java.util.concurrent;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Completion callbacks run after publishing a result and outside its monitor. */
public class CompletableFuture<T> implements Future<T>,CompletionStage<T> {
    private boolean done;private T value;private Throwable failure;
    private final List<Runnable> listeners=new ArrayList<>();
    private boolean finish(T value,Throwable failure){
        List<Runnable> callbacks;
        synchronized(this){if(done)return false;this.value=value;this.failure=failure;done=true;callbacks=new ArrayList<>(listeners);listeners.clear();notifyAll();}
        for(Runnable callback:callbacks)callback.run();return true;
    }
    private void listen(Runnable callback){synchronized(this){if(!done){listeners.add(callback);return;}}callback.run();}
    public boolean complete(T value){return finish(value,null);}
    public boolean completeExceptionally(Throwable failure){return finish(null,Objects.requireNonNull(failure));}
    public boolean cancel(boolean interrupt){return completeExceptionally(new CancellationException());}
    public synchronized boolean isDone(){return done;}
    public synchronized boolean isCancelled(){return failure instanceof CancellationException;}
    public synchronized boolean isCompletedExceptionally(){return done&&failure!=null;}
    private T result()throws ExecutionException {if(failure instanceof CancellationException c)throw c;if(failure!=null)throw new ExecutionException(failure instanceof CompletionException&&failure.getCause()!=null?failure.getCause():failure);return value;}
    public synchronized T get()throws InterruptedException,ExecutionException{while(!done)wait();return result();}
    public synchronized T get(long timeout,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{long nanos=unit.toNanos(timeout),start=System.nanoTime();while(!done){long left=nanos-(System.nanoTime()-start);if(left<=0)throw new TimeoutException();TimeUnit.NANOSECONDS.timedWait(this,left);}return result();}
    public T join(){boolean interrupted=false;try{synchronized(this){while(!done)try{wait();}catch(InterruptedException e){interrupted=true;}if(failure instanceof CancellationException c)throw c;if(failure!=null)throw wrap(failure);return value;}}finally{if(interrupted)Thread.currentThread().interrupt();}}
    public synchronized T getNow(T otherwise){return done?join():otherwise;}
    private static CompletionException wrap(Throwable error){return error instanceof CompletionException c?c:new CompletionException(error);}
    public static <U> CompletableFuture<U> completedFuture(U value){var result=new CompletableFuture<U>();result.complete(value);return result;}
    public static <U> CompletableFuture<U> failedFuture(Throwable error){var result=new CompletableFuture<U>();result.completeExceptionally(error);return result;}
    public static CompletableFuture<Void> runAsync(Runnable work){return runAsync(work,ForkJoinPool.commonPool());}
    public static CompletableFuture<Void> runAsync(Runnable work,Executor executor){Objects.requireNonNull(work);return supplyAsync(()->{work.run();return null;},executor);}
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> work){return supplyAsync(work,ForkJoinPool.commonPool());}
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> work,Executor executor){Objects.requireNonNull(work);var result=new CompletableFuture<U>();Objects.requireNonNull(executor).execute(()->{if(result.isDone())return;try{result.complete(work.get());}catch(Throwable e){result.completeExceptionally(wrap(e));}});return result;}
    public <U> CompletableFuture<U> handle(BiFunction<? super T,Throwable,? extends U> fn){Objects.requireNonNull(fn);var result=new CompletableFuture<U>();listen(()->{if(result.isDone())return;try{result.complete(fn.apply(value,failure));}catch(Throwable e){result.completeExceptionally(wrap(e));}});return result;}
    public <U> CompletableFuture<U> thenApply(Function<? super T,? extends U> fn){Objects.requireNonNull(fn);return handle((value,error)->{if(error!=null)throw wrap(error);return fn.apply(value);});}
    public CompletableFuture<Void> thenAccept(Consumer<? super T> fn){Objects.requireNonNull(fn);return thenApply(value->{fn.accept(value);return null;});}
    public CompletableFuture<Void> thenRun(Runnable work){Objects.requireNonNull(work);return thenApply(value->{work.run();return null;});}
    public CompletableFuture<T> exceptionally(Function<Throwable,? extends T> fn){Objects.requireNonNull(fn);return handle((value,error)->error==null?value:fn.apply(error));}
    public CompletableFuture<T> whenComplete(BiConsumer<? super T,? super Throwable> fn){Objects.requireNonNull(fn);return handle((value,error)->{try{fn.accept(value,error);}catch(Throwable e){if(error==null)throw wrap(e);}if(error!=null)throw wrap(error);return value;});}
    public <U> CompletableFuture<U> thenCompose(Function<? super T,? extends CompletionStage<U>> fn){Objects.requireNonNull(fn);var result=new CompletableFuture<U>();listen(()->{if(result.isDone())return;try{if(failure!=null){result.completeExceptionally(wrap(failure));return;}CompletableFuture<U> next=Objects.requireNonNull(fn.apply(value)).toCompletableFuture();next.listen(()->{if(next.failure!=null)result.completeExceptionally(wrap(next.failure));else result.complete(next.value);});}catch(Throwable e){result.completeExceptionally(wrap(e));}});return result;}
    public CompletableFuture<T> toCompletableFuture(){return this;}
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... futures){for(var f:futures)Objects.requireNonNull(f);var result=new CompletableFuture<Void>();if(futures.length==0){result.complete(null);return result;}int[] remaining={futures.length};Throwable[] error={null};for(var future:futures)future.listen(()->{boolean last;synchronized(remaining){if(error[0]==null)error[0]=future.failure;last=--remaining[0]==0;}if(last){if(error[0]!=null)result.completeExceptionally(wrap(error[0]));else result.complete(null);}});return result;}
}
