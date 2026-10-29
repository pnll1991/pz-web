package org.teavm.runtime;

import java.util.Arrays;
import org.teavm.interop.AsyncCallback;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@StaticInit
public final class Fiber {
   public static final int STATE_RUNNING = 0;
   public static final int STATE_SUSPENDING = 1;
   public static final int STATE_RESUMING = 2;
   public static int userThreadCount = 1;
   private int[] intValues;
   private int intTop;
   private long[] longValues;
   private int longTop;
   private float[] floatValues;
   private int floatTop;
   private double[] doubleValues;
   private int doubleTop;
   private Object[] objectValues;
   private int objectTop;
   private int state;
   private Fiber.FiberRunner runner;
   private Object result;
   private Throwable exception;
   private boolean isPendingResume;
   private boolean daemon;
   private static Fiber current;
   private static Fiber.PendingCall lastPendingCall;

   private Fiber(Fiber.FiberRunner runner, boolean daemon) {
      this.runner = runner;
      this.daemon = daemon;
   }

   public void push(int value) {
      if (this.intValues == null) {
         this.intValues = new int[4];
      } else if (this.intTop + 1 == this.intValues.length) {
         this.intValues = Arrays.copyOf(this.intValues, this.intValues.length * 3 / 2);
      }

      this.intValues[this.intTop++] = value;
   }

   public void push(long value) {
      if (this.longValues == null) {
         this.longValues = new long[4];
      } else if (this.longTop + 1 == this.longValues.length) {
         this.longValues = Arrays.copyOf(this.longValues, this.longValues.length * 3 / 2);
      }

      this.longValues[this.longTop++] = value;
   }

   public void push(float value) {
      if (this.floatValues == null) {
         this.floatValues = new float[4];
      } else if (this.floatTop + 1 == this.floatValues.length) {
         this.floatValues = Arrays.copyOf(this.floatValues, this.floatValues.length * 3 / 2);
      }

      this.floatValues[this.floatTop++] = value;
   }

   public void push(double value) {
      if (this.doubleValues == null) {
         this.doubleValues = new double[4];
      } else if (this.doubleTop + 1 == this.doubleValues.length) {
         this.doubleValues = Arrays.copyOf(this.doubleValues, this.doubleValues.length * 3 / 2);
      }

      this.doubleValues[this.doubleTop++] = value;
   }

   public void push(Object value) {
      if (this.objectValues == null) {
         this.objectValues = new Object[4];
      } else if (this.objectTop + 1 == this.objectValues.length) {
         this.objectValues = Arrays.copyOf(this.objectValues, this.objectValues.length * 3 / 2);
      }

      this.objectValues[this.objectTop++] = value;
   }

   public static void reversePush(int value, Fiber fiber) {
      fiber.push(value);
   }

   public static void reversePush(long value, Fiber fiber) {
      fiber.push(value);
   }

   public static void reversePush(float value, Fiber fiber) {
      fiber.push(value);
   }

   public static void reversePush(double value, Fiber fiber) {
      fiber.push(value);
   }

   public static void reversePush(Object value, Fiber fiber) {
      fiber.push(value);
   }

   public static void reversePush(Fiber.PlatformObject value, Fiber fiber) {
      fiber.push(new Fiber.PlatformObjectWrapper(value));
   }

   public static void reversePush(Fiber.PlatformFunction value, Fiber fiber) {
      fiber.push(new Fiber.PlatformFunctionWrapper(value));
   }

   public static void reversePush(Fiber.PlatformException value, Fiber fiber) {
      fiber.push(new Fiber.PlatformExceptionWrapper(value));
   }

   @Unmanaged
   public int popInt() {
      return this.intValues[--this.intTop];
   }

   @Unmanaged
   public long popLong() {
      return this.longValues[--this.longTop];
   }

   @Unmanaged
   public float popFloat() {
      return this.floatValues[--this.floatTop];
   }

   @Unmanaged
   public double popDouble() {
      return this.doubleValues[--this.doubleTop];
   }

   @Unmanaged
   public Object popObject() {
      Object result = this.objectValues[--this.objectTop];
      this.objectValues[this.objectTop] = null;
      return result;
   }

   public Fiber.PlatformObject popPlatformObject() {
      Fiber.PlatformObjectWrapper wrapper = (Fiber.PlatformObjectWrapper)this.popObject();
      return wrapper.object;
   }

   public Fiber.PlatformFunction popPlatformFunction() {
      Fiber.PlatformFunctionWrapper wrapper = (Fiber.PlatformFunctionWrapper)this.popObject();
      return wrapper.object;
   }

   public Fiber.PlatformException popPlatformException() {
      Fiber.PlatformExceptionWrapper wrapper = (Fiber.PlatformExceptionWrapper)this.popObject();
      return wrapper.object;
   }

   @Unmanaged
   public static Fiber current() {
      return current;
   }

   @Unmanaged
   public boolean isSuspending() {
      return this.state == 1;
   }

   @Unmanaged
   public static boolean isPresentAndSuspending(Fiber fiber) {
      return fiber != null && fiber.isSuspending();
   }

   @Unmanaged
   public boolean isResuming() {
      return this.state == 2;
   }

   @Unmanaged
   public static boolean isPresentAndResuming(Fiber fiber) {
      return fiber != null && fiber.isResuming();
   }

   @Unmanaged
   public static boolean getBoolean(Object v) {
      return v != null ? (Boolean)v : false;
   }

   @Unmanaged
   public static byte getByte(Object v) {
      return v != null ? (Byte)v : 0;
   }

   @Unmanaged
   public static short getShort(Object v) {
      return v != null ? (Short)v : 0;
   }

   @Unmanaged
   public static int getInt(Object v) {
      return v != null ? (Integer)v : 0;
   }

   @Unmanaged
   public static char getChar(Object v) {
      return v != null ? (Character)v : '\u0000';
   }

   @Unmanaged
   public static long getLong(Object v) {
      return v != null ? (Long)v : 0L;
   }

   @Unmanaged
   public static float getFloat(Object v) {
      return v != null ? (Float)v : 0.0F;
   }

   @Unmanaged
   public static double getDouble(Object v) {
      return v != null ? (Double)v : 0.0;
   }

   public static Object suspend(Fiber.AsyncCall call) throws Throwable {
      Fiber fiber = current();
      if (fiber == null) {
         throw new IllegalStateException(
            "Suspension point reached from non-threading context (perhaps, from native JS method). See https://teavm.org/docs/runtime/coroutines.html ('Interaction with JavaScript' section)"
         );
      }

      Thread javaThread = Thread.currentThread();
      if (fiber.isResuming()) {
         fiber.state = 0;
         if (fiber.exception != null) {
            throw fiber.exception;
         } else {
            return fiber.result;
         }
      } else {
         Fiber.PendingCall pendingCall = new Fiber.PendingCall(call, lastPendingCall);
         if (lastPendingCall != null) {
            lastPendingCall.next = pendingCall;
         }

         lastPendingCall = pendingCall;
         fiber.state = 1;
         pendingCall.callback = new Fiber.AsyncCallbackImpl(pendingCall, javaThread, fiber);
         call.run(pendingCall.callback);
         return null;
      }
   }

   static native void setCurrentThread(Thread var0);

   public static void start(Fiber.FiberRunner runner, boolean daemon) {
      new Fiber(runner, daemon).start();
   }

   static void startMain(String[] args) {
      start(() -> runMain(args), false);
   }

   public static native void runMain(String[] var0);

   private void start() {
      Fiber former = current;
      current = this;

      while (true) {
         this.runner.run();
         if (!this.isPendingResume) {
            current = former;
            if (!this.isSuspending() && !this.daemon && --userThreadCount == 0) {
               EventQueue.stop();
            }

            return;
         }

         this.isPendingResume = false;
         this.state = 2;
      }
   }

   void resume() {
      this.state = 2;
      this.start();
   }

   public interface AsyncCall {
      void run(AsyncCallback<?> var1);
   }

   static class AsyncCallbackImpl implements AsyncCallback<Object> {
      Fiber.PendingCall pendingCall;
      Thread javaThread;
      Fiber fiber;

      AsyncCallbackImpl(Fiber.PendingCall pendingCall, Thread javaThread, Fiber fiber) {
         this.pendingCall = pendingCall;
         this.javaThread = javaThread;
         this.fiber = fiber;
      }

      public void complete(Object result) {
         if (Fiber.current == this.fiber) {
            this.fiber.result = result;
            this.fiber.isPendingResume = true;
            this.removePendingCall();
         } else {
            Fiber.setCurrentThread(this.javaThread);
            this.javaThread = null;
            Fiber fiber = this.fiber;
            this.fiber = null;
            fiber.result = result;
            this.removePendingCall();
            fiber.resume();
         }
      }

      public void error(Throwable e) {
         if (Fiber.current == this.fiber) {
            this.fiber.exception = e;
            this.fiber.isPendingResume = true;
            this.removePendingCall();
         } else {
            Fiber.setCurrentThread(this.javaThread);
            this.javaThread = null;
            Fiber fiber = this.fiber;
            this.fiber = null;
            fiber.exception = e;
            this.removePendingCall();
            fiber.resume();
         }
      }

      private void removePendingCall() {
         if (this.pendingCall.previous != null) {
            this.pendingCall.previous.next = this.pendingCall.next;
         }

         if (this.pendingCall.next != null) {
            this.pendingCall.next.previous = this.pendingCall.previous;
         }

         if (this.pendingCall == Fiber.lastPendingCall) {
            Fiber.lastPendingCall = this.pendingCall.previous;
         }

         this.pendingCall = null;
      }
   }

   public interface FiberRunner {
      void run();
   }

   static class PendingCall {
      Fiber.AsyncCall value;
      Fiber.PendingCall next;
      Fiber.PendingCall previous;
      Fiber.AsyncCallbackImpl callback;

      PendingCall(Fiber.AsyncCall value, Fiber.PendingCall previous) {
         this.value = value;
         this.next = null;
         this.previous = previous;
      }
   }

   public static class PlatformException {
   }

   public static class PlatformExceptionWrapper {
      public final Fiber.PlatformException object;

      PlatformExceptionWrapper(Fiber.PlatformException object) {
         this.object = object;
      }
   }

   public static class PlatformFunction {
   }

   public static class PlatformFunctionWrapper {
      public final Fiber.PlatformFunction object;

      PlatformFunctionWrapper(Fiber.PlatformFunction object) {
         this.object = object;
      }
   }

   public static class PlatformObject {
   }

   public static class PlatformObjectWrapper {
      public final Fiber.PlatformObject object;

      PlatformObjectWrapper(Fiber.PlatformObject object) {
         this.object = object;
      }
   }
}
