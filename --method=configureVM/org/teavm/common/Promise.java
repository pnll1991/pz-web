package org.teavm.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

public class Promise<T> {
   private static ThreadLocal<Queue<Runnable>> processing = new ThreadLocal<>();
   public static final Promise<Void> VOID = of(null);
   private T value;
   private Throwable error;
   private Promise.State state = Promise.State.PENDING;
   private List<Promise.Then<T>> thenList;
   private List<Promise.Catch> catchList;

   Promise() {
   }

   public static <T> Promise<T> of(T value) {
      Promise<T> promise = new Promise<>();
      promise.complete(value);
      return promise;
   }

   public static Promise<?> error(Throwable e) {
      Promise<?> promise = new Promise();
      promise.completeWithError(e);
      return promise;
   }

   public static Promise<Void> allVoid(Collection<Promise<Void>> promises) {
      if (promises.isEmpty()) {
         return VOID;
      }

      Promise.AllVoidFunction all = new Promise.AllVoidFunction(promises.size());

      for (Promise<?> promise : promises) {
         promise.then(all.thenF).catchError(all.catchF);
      }

      return all.result;
   }

   public static <T> Promise<List<T>> all(Collection<Promise<T>> promises) {
      if (promises.isEmpty()) {
         return of(Collections.emptyList());
      }

      Promise.AllFunction<T> all = new Promise.AllFunction<>(promises.size());
      int i = 0;

      for (Promise<T> promise : promises) {
         promise.then(all.thenF(i++)).catchError(all.catchF);
      }

      return all.result;
   }

   public <S> Promise<S> then(Function<? super T, S> f) {
      Promise<S> result = new Promise();
      if (this.state != Promise.State.PENDING && this.state != Promise.State.WAITING_PROMISE) {
         this.passValue(f, result);
      } else {
         if (this.thenList == null) {
            this.thenList = new ArrayList<>();
         }

         this.thenList.add(new Promise.Then<>(f, result, false));
      }

      return result;
   }

   public Promise<Void> thenVoid(Consumer<T> f) {
      return this.then(r -> {
         f.accept((T)r);
         return null;
      });
   }

   public <S> Promise<S> thenAsync(Function<T, Promise<S>> f) {
      Promise<S> result = new Promise();
      if (this.state != Promise.State.PENDING && this.state != Promise.State.WAITING_PROMISE) {
         if (this.state == Promise.State.COMPLETED) {
            this.passValueAsync(f, result);
         }
      } else {
         if (this.thenList == null) {
            this.thenList = new ArrayList<>();
         }

         this.thenList.add(new Promise.Then<>(f, result, true));
      }

      return result;
   }

   public <S> Promise<S> catchError(Function<Throwable, S> f) {
      Promise<S> result = new Promise();
      if (this.state != Promise.State.PENDING && this.state != Promise.State.WAITING_PROMISE) {
         if (this.state == Promise.State.ERRORED) {
            this.passError(f, result);
         }
      } else {
         if (this.catchList == null) {
            this.catchList = new ArrayList<>();
         }

         this.catchList.add(new Promise.Catch(f, result));
      }

      return result;
   }

   public Promise<Void> catchVoid(Consumer<Throwable> f) {
      return this.catchError(e -> {
         f.accept(e);
         return null;
      });
   }

   <S> void passValue(Function<? super T, S> f, Promise<? super S> target) {
      this.runAction(() -> {
         if (this.state == Promise.State.COMPLETED) {
            S next;
            try {
               next = f.apply(this.value);
            } catch (Throwable e) {
               target.completeWithError(e);
               return;
            }

            target.complete(next);
         } else {
            target.completeWithError(this.error);
         }
      });
   }

   <S> void passValueAsync(Function<T, Promise<S>> f, Promise<S> target) {
      this.runAction(() -> {
         if (this.state == Promise.State.COMPLETED) {
            Promise<S> next;
            try {
               next = f.apply(this.value);
            } catch (Throwable e) {
               target.completeWithError(e);
               return;
            }

            target.completeAsync(next);
         } else {
            target.completeWithError(this.error);
         }
      });
   }

   <S> void passError(Function<Throwable, S> f, Promise<? super S> target) {
      this.runAction(() -> {
         S next;
         try {
            next = f.apply(this.error);
         } catch (Throwable e) {
            target.completeWithError(e);
            return;
         }

         target.complete(next);
      });
   }

   void complete(T value) {
      if (this.state != Promise.State.PENDING) {
         throw new IllegalStateException("Already completed");
      }

      this.completeImpl(value);
   }

   void completeAsync(Promise<T> value) {
      if (this.state != Promise.State.PENDING) {
         throw new IllegalStateException("Already completed");
      }

      this.state = Promise.State.WAITING_PROMISE;
      value.then(result -> {
         this.completeImpl((T)result);
         return null;
      }).catchError(e -> {
         this.completeWithErrorImpl(e);
         return null;
      });
   }

   private void completeImpl(T value) {
      this.runAction(() -> {
         this.state = Promise.State.COMPLETED;
         this.value = value;
         if (this.thenList != null) {
            List<Promise.Then<T>> list = this.thenList;
            this.thenList = null;

            for (Promise.Then<T> then : list) {
               if (then.promise) {
                  this.passValueAsync((Function<T, Promise<?>>)then.f, then.target);
               } else {
                  this.passValue(then.f, then.target);
               }
            }
         }

         this.catchList = null;
      });
   }

   void completeWithError(Throwable e) {
      if (this.state != Promise.State.PENDING) {
         throw new IllegalStateException("Already completed");
      }

      this.completeWithErrorImpl(e);
   }

   void completeWithErrorImpl(Throwable e) {
      this.runAction(() -> {
         this.state = Promise.State.ERRORED;
         this.error = e;
         if (this.catchList != null) {
            List<Promise.Catch> list = this.catchList;
            this.thenList = null;

            for (Promise.Catch c : list) {
               this.passError(c.f, c.target);
            }
         } else {
            e.printStackTrace();
         }

         this.thenList = null;
      });
   }

   private void runAction(Runnable action) {
      Queue<Runnable> queue = processing.get();
      if (queue != null) {
         queue.add(action);
      } else {
         queue = new ArrayDeque<>();
         queue.add(action);
         processing.set(queue);

         while (!queue.isEmpty()) {
            queue.remove().run();
         }

         processing.remove();
      }
   }

   public static void runNow(Runnable runnable) {
      Queue<Runnable> queue = processing.get();
      if (queue == null) {
         runnable.run();
      } else {
         processing.remove();
         runnable.run();
         processing.set(queue);
      }
   }

   static class AllFunction<T> {
      Promise<List<T>> result = new Promise<>();
      List<T> list = new ArrayList<>();
      int count;
      boolean error;
      Function<Throwable, Void> catchF = e -> {
         if (!this.error) {
            this.error = true;
            this.result.completeWithError(e);
         }

         return null;
      };

      AllFunction(int count) {
         this.count = count;
         this.list.addAll(Collections.nCopies(count, null));
      }

      Function<T, Void> thenF(int index) {
         return v -> {
            if (!this.error) {
               this.list.set(index, v);
               if (--this.count == 0) {
                  this.result.complete(this.list);
               }
            }

            return null;
         };
      }
   }

   static class AllVoidFunction {
      Promise<Void> result = new Promise<>();
      int count;
      boolean error;
      Function<Object, Void> thenF = v -> {
         if (!this.error && --this.count == 0) {
            this.result.complete(null);
         }

         return null;
      };
      Function<Throwable, Void> catchF = e -> {
         if (!this.error) {
            this.error = true;
            this.result.completeWithError(e);
         }

         return null;
      };

      AllVoidFunction(int count) {
         this.count = count;
      }
   }

   static class Catch {
      Function<Throwable, ?> f;
      Promise<?> target;

      Catch(Function<Throwable, ?> f, Promise<?> target) {
         this.f = f;
         this.target = target;
      }
   }

   enum State {
      PENDING,
      WAITING_PROMISE,
      COMPLETED,
      ERRORED;
   }

   static class Then<T> {
      Function<? super T, ?> f;
      Promise<?> target;
      boolean promise;

      Then(Function<? super T, ?> f, Promise<?> target, boolean promise) {
         this.f = f;
         this.target = target;
         this.promise = promise;
      }
   }
}
