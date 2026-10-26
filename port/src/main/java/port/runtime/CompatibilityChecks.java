package port.runtime;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/** Uses standard API names so browser tests exercise the actual substitution path. */
public final class CompatibilityChecks {
    public static String filesAndMath() throws Exception {
        try { System.loadLibrary("fixture-unavailable"); throw new AssertionError("native load claimed success"); }
        catch (UnsatisfiedLinkError expected) { if (!expected.getMessage().contains("fixture-unavailable")) throw new AssertionError("lost library name"); }
        if (Runtime.getRuntime().maxMemory() <= 0) throw new AssertionError("Chrome heap limit");
        if (!System.getenv().isEmpty()) throw new AssertionError("host environment leaked");
        try { new ProcessBuilder("fixture-command").start(); throw new AssertionError("OS process claimed success"); }
        catch (java.io.IOException expected) { if (!expected.getMessage().contains("ProcessBuilder.start")) throw new AssertionError("lost process operation"); }
        if (!ClassLoader.getSystemClassLoader().loadClass("port.demo.ReflectionProbe").getName().equals("port.demo.ReflectionProbe")) throw new AssertionError("AOT class loading");
        String highBytes = new String(new byte[]{0,(byte)128,(byte)255}, 0, 0, 3);
        if (highBytes.charAt(1) != 128 || highBytes.charAt(2) != 255) throw new AssertionError("legacy String byte constructor");
        var uuid = new UUID(0x1020304050607080L, -1L);
        if (!uuid.toString().equals("10203040-5060-7080-ffff-ffffffffffff") || uuid.getMostSignificantBits() != 0x1020304050607080L || uuid.getLeastSignificantBits() != -1L)
            throw new AssertionError("UUID bit round trip");
        var path = java.nio.file.Path.of("/Zomboid/channel-check.bin");
        try (var output = new java.io.FileOutputStream(path.toFile())) {
            output.write(new byte[]{1, 2, 3}); var channel = output.getChannel();
            if (channel != output.getChannel() || channel.position() != 3) throw new AssertionError("shared stream channel");
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{9}), 1);
            if (channel.position() != 3) throw new AssertionError("positional write changed stream offset");
            channel.force(true);
        }
        try (var input = new java.io.FileInputStream(path.toFile())) {
            var channel = input.getChannel();
            if (input.read() != 1 || channel.position() != 1) throw new AssertionError("stream read position");
            var data = java.nio.ByteBuffer.allocate(2);
            if (channel.read(data) != 2 || data.get(0) != 9 || data.get(1) != 3) throw new AssertionError("channel bytes");
            if (channel.read(java.nio.ByteBuffer.allocate(0)) != 0 || channel.read(data.clear()) != -1) throw new AssertionError("zero read / EOF");
            channel.close(); if (channel.isOpen()) throw new AssertionError("channel close");
            try { input.read(); throw new AssertionError("stream stayed open"); } catch (java.io.IOException expected) { }
        }
        try (var channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.WRITE)) {
            try { channel.tryLock(); throw new AssertionError("OS file lock claimed success"); }
            catch (UnsupportedOperationException expected) { if (!expected.getMessage().contains("FileChannel.tryLock")) throw new AssertionError("lost lock operation"); }
            if (channel.size() != 3) throw new AssertionError("WRITE truncated file");
            channel.truncate(2); if (channel.size() != 2) throw new AssertionError("truncate");
            channel.force(true);
        }
        try (var file = new java.io.RandomAccessFile(path.toFile(), "rw")) {
            var channel = file.getChannel(); channel.position(1); if (file.getFilePointer() != 1) throw new AssertionError("RandomAccessFile shared position");
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{9})); channel.force(true);
        }
        try (var log = new java.io.PrintStream(new java.io.File("/Zomboid/printstream.txt"))) { log.print("log ñ"); }
        if (!java.nio.file.Files.readString(java.nio.file.Path.of("/Zomboid/printstream.txt")).equals("log ñ")) throw new AssertionError("PrintStream(File)");
        if (Double.doubleToLongBits(Math.fma(Double.MAX_VALUE, 2, -Double.MAX_VALUE)) != Double.doubleToLongBits(Double.MAX_VALUE)) throw new AssertionError("fma overflow cancellation");
        if (Float.floatToIntBits(Math.fma(-0.0f, 1.0f, -0.0f)) != Float.floatToIntBits(-0.0f)) throw new AssertionError("fma signed zero");
        if (!java.util.regex.Pattern.compile("[0-9]+").matcher("a12b3").replaceAll(m -> "[" + m.group() + "]").equals("a[12]b[3]")) throw new AssertionError("Matcher function");
        var crc = new java.util.zip.CRC32(); var buffer = java.nio.ByteBuffer.wrap(new byte[]{1,2,3});
        crc.update(buffer); if (crc.getValue() != 1438416925L || buffer.hasRemaining()) throw new AssertionError("CRC32 ByteBuffer");
        return "channels,force,printstream,fma,matcher,crc:OK";
    }
    public static String concurrency() throws Exception {
        var group = new ThreadGroup("fixture-group"); String[] seen = {"", ""};
        Thread member = new Thread(group,()->{
            seen[0]=Thread.currentThread().getThreadGroup().getName();
            seen[1]=new Thread(()->{}).getThreadGroup().getName();
        },"fixture-member");
        if (member.getThreadGroup() != group || group.activeCount() != 0 || member.isAlive()) throw new AssertionError("unstarted thread group");
        member.join(); // Joining an unstarted thread must return immediately.
        member.start(); member.join();
        if (!seen[0].equals("fixture-group") || !seen[1].equals("fixture-group") || group.activeCount() != 0 || member.getThreadGroup() != null) throw new AssertionError("thread group lifecycle");
        try { member.start(); throw new AssertionError("thread restarted"); } catch (IllegalThreadStateException expected) { }
        var queue = new ConcurrentLinkedQueue<Integer>();
        queue.add(1); queue.add(2); var iterator = queue.iterator(); queue.add(3);
        if (iterator.next() != 1 || queue.poll() != 1) throw new AssertionError("queue order");
        var deque = new ConcurrentLinkedDeque<Integer>(); deque.addFirst(1); deque.addLast(2);
        if (deque.removeLast() != 2 || deque.pop() != 1) throw new AssertionError("deque order");
        var set = new CopyOnWriteArraySet<Integer>(); set.add(1); set.add(1);
        if (set.size() != 1) throw new AssertionError("set uniqueness");
        var keys = ConcurrentHashMap.<Integer>newKeySet(); keys.add(1); keys.add(1); keys.add(2);
        if (keys.size() != 2 || !keys.getMap().containsKey(1)) throw new AssertionError("concurrent key set");
        var cancelled = new FutureTask<Integer>(() -> { throw new AssertionError("cancelled task ran"); });
        cancelled.cancel(true); cancelled.run();
        try { cancelled.get(); throw new AssertionError("missing cancellation"); } catch (CancellationException expected) { }
        var pool = Executors.newSingleThreadExecutor();
        int[] sum = {0};
        try {
            for (int i = 1; i <= 10; i++) { int value = i; pool.submit(() -> { sum[0] += value; }); }
            if (pool.submit(() -> sum[0]).get(5, TimeUnit.SECONDS) != 55) throw new AssertionError("executor order");
        } finally { pool.shutdown(); }
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) throw new AssertionError("executor termination");
        var lock = new ReentrantLock(); var condition = lock.newCondition(); boolean[] signalled = {false};
        lock.lock(); lock.lock();
        Thread other = new Thread(() -> { lock.lock(); try { signalled[0] = true; condition.signal(); } finally { lock.unlock(); } });
        other.start();
        while (!signalled[0]) if (!condition.await(5, TimeUnit.SECONDS)) throw new AssertionError("condition timeout");
        if (lock.getHoldCount() != 2) throw new AssertionError("condition lost reentrant ownership");
        lock.unlock(); lock.unlock(); other.join();
        return "queues,futures,executor,condition:OK";
    }
}
