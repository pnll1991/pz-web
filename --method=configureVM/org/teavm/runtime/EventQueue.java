package org.teavm.runtime;

import java.util.Arrays;
import org.teavm.backend.c.intrinsic.RuntimeInclude;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.interop.Intrinsified;
import org.teavm.interop.StaticInit;

@StaticInit
public final class EventQueue {
   private static EventQueue.Node[] data = new EventQueue.Node[16];
   private static int size;
   private static boolean finished;
   private static int idGenerator;

   private EventQueue() {
   }

   public static void offer(EventQueue.Event action) {
      offer(action, System.currentTimeMillis());
   }

   public static int offer(EventQueue.Event action, long time) {
      if (PlatformDetector.isWebAssemblyGC()) {
         return offerWasmGC(action, time);
      }

      ensureCapacity(size + 1);
      int current = size;

      while (current > 0) {
         int parent = (current - 1) / 2;
         if (time >= data[parent].time) {
            break;
         }

         data[current] = data[parent];
         current = parent;
      }

      int id = idGenerator++;
      data[current] = new EventQueue.Node(id, action, time);
      size++;
      if (current == 0) {
         interrupt();
      }

      return id;
   }

   @Intrinsified
   private static native int offerWasmGC(EventQueue.Event var0, double var1);

   private static void run(EventQueue.Event action) {
      action.run();
   }

   public static void kill(int id) {
      for (int i = 0; i < size; i++) {
         if (data[i].id == id) {
            remove(i);
            break;
         }
      }
   }

   @Import(name = "kill", module = "teavmAsync")
   private static native int killWasmGC(int var0);

   public static void process() {
      while (size > 0 && !finished) {
         next();
      }
   }

   @Export(name = "teavm_processQueue")
   public static long processSingle() {
      if (size == 0) {
         return -1L;
      } else {
         EventQueue.Node node = data[0];
         long currentTime = System.currentTimeMillis();
         if (node.time <= System.currentTimeMillis()) {
            remove(0);
            node.event.run();
            return size == 0 ? -1L : Math.max(0L, node.time - currentTime);
         } else {
            return node.time - currentTime;
         }
      }
   }

   @Export(name = "teavm_stopped")
   public static boolean isStopped() {
      return finished;
   }

   public static void stop() {
      finished = true;
   }

   private static void next() {
      while (data.length == 0) {
         waitUntil(System.currentTimeMillis() + 1000L);
      }

      EventQueue.Node node = data[0];
      waitUntil(node.time);
      if (node.time <= System.currentTimeMillis()) {
         remove(0);
         node.event.run();
      }
   }

   private static void remove(int index) {
      size--;
      if (index < size) {
         data[index] = data[size];
         update(index);
      }

      data[size] = null;
   }

   private static void update(int index) {
      EventQueue.Node item = data[index];

      while (true) {
         int left = index * 2 + 1;
         int right = left + 1;
         if (left >= size) {
            break;
         }

         int next;
         if (right < size && data[left].time >= data[right].time) {
            next = right;
         } else {
            next = left;
         }

         if (item.time <= data[next].time) {
            break;
         }

         data[index] = data[next];
         index = next;
      }

      data[index] = item;
   }

   private static void waitUntil(long time) {
      long diff = time - System.currentTimeMillis();
      if (diff > 0L) {
         waitFor(diff);
      }
   }

   @Import(name = "teavm_waitFor")
   @RuntimeInclude("fiber.h")
   private static native void waitFor(long var0);

   @Import(name = "teavm_interrupt", module = "teavm")
   @RuntimeInclude("fiber.h")
   private static native void interrupt();

   private static void ensureCapacity(int capacity) {
      if (data.length < capacity) {
         capacity = Math.max(capacity, data.length * 3 / 2);
         data = Arrays.copyOf(data, capacity);
      }
   }

   public interface Event {
      void run();
   }

   static class Node {
      final int id;
      final EventQueue.Event event;
      final long time;

      Node(int id, EventQueue.Event event, long time) {
         this.id = id;
         this.event = event;
         this.time = time;
      }
   }
}
