package port.compat.java.lang;

import java.util.*;

/** Group metadata and lifecycle for TeaVM cooperative threads, with weak membership. */
public class ThreadGroup implements Thread.UncaughtExceptionHandler {
    private static final ThreadGroup ROOT = new ThreadGroup(null, "browser", true);
    private record Member(ThreadGroup group, boolean started) { }
    private static final Map<Thread,Member> MEMBERS = new WeakHashMap<>();
    private final String name;
    private final ThreadGroup parent;
    private final Set<ThreadGroup> children = Collections.newSetFromMap(new WeakHashMap<>());
    private int maximumPriority = Thread.MAX_PRIORITY;
    public ThreadGroup(String name) { this(groupOf(Thread.currentThread()),name); }
    public ThreadGroup(ThreadGroup parent, String name) { this(Objects.requireNonNull(parent),name,false); }
    private ThreadGroup(ThreadGroup parent, String name, boolean root) {
        this.parent = parent; this.name = Objects.requireNonNull(name);
        if (parent != null) { maximumPriority = parent.maximumPriority; parent.children.add(this); }
    }
    public final String getName() { return name; }
    public final ThreadGroup getParent() { return parent; }
    public final int getMaxPriority() { return maximumPriority; }
    public final void setMaxPriority(int value) {
        if (value < Thread.MIN_PRIORITY || value > Thread.MAX_PRIORITY) return;
        maximumPriority = parent == null ? value : Math.min(value,parent.maximumPriority);
        for (var child : new ArrayList<>(children)) child.setMaxPriority(value);
    }
    public final boolean parentOf(ThreadGroup other) {
        for (; other != null; other = other.parent) if (other == this) return true;
        return false;
    }
    public static void registerDefault(Thread thread) {
        Thread current = Thread.currentThread();
        // TThread creates main before assigning its static currentThread field.
        // No thread can contend yet, and an asynchronous monitor cannot have a
        // null owner. Keep this bootstrap path outside the normal monitor.
        if (current == null) {
            MEMBERS.put(thread,new Member(ROOT,true));
            return;
        }
        synchronized (ThreadGroup.class) {
            ThreadGroup group = groupOf(current);
            MEMBERS.put(thread,new Member(group == null ? ROOT : group,false));
        }
    }
    public static synchronized void assign(Thread thread, ThreadGroup group) {
        if (group != null) MEMBERS.put(thread,new Member(group,false));
    }
    public static synchronized void beforeStart(Thread thread) {
        Member member = MEMBERS.get(thread);
        if (member != null && member.started) throw new IllegalThreadStateException("Thread already started");
        MEMBERS.put(thread,new Member(member == null ? ROOT : member.group,true));
    }
    public static synchronized boolean alive(Thread thread, boolean running) {
        Member member = MEMBERS.get(thread);
        return running && (member == null || member.started);
    }
    public static synchronized ThreadGroup groupOf(Thread thread) {
        if (thread == null) return ROOT;
        Member member = MEMBERS.get(thread);
        if (member == null) return ROOT;
        return member.started && !thread.isAlive() ? null : member.group;
    }
    public static Thread.UncaughtExceptionHandler handler(Thread thread, Thread.UncaughtExceptionHandler explicit) {
        return explicit != null ? explicit : groupOf(thread);
    }
    private List<Thread> threads(boolean recurse) {
        var result = new ArrayList<Thread>();
        synchronized (ThreadGroup.class) {
            for (var entry : MEMBERS.entrySet()) {
                var member = entry.getValue();
                if (member.started && entry.getKey().isAlive() && (recurse ? parentOf(member.group) : member.group == this)) result.add(entry.getKey());
            }
        }
        return result;
    }
    public static synchronized Map<Thread,StackTraceElement[]> stackTraces() {
        var result = new HashMap<Thread,StackTraceElement[]>();
        for(var entry:MEMBERS.entrySet())if(entry.getValue().started && entry.getKey().isAlive())result.put(entry.getKey(),entry.getKey().getStackTrace());
        Thread current=Thread.currentThread();result.put(current,current.getStackTrace());return result;
    }
    public int activeCount() { return threads(true).size(); }
    public int activeGroupCount() { int count = children.size(); for (var child : new ArrayList<>(children)) count += child.activeGroupCount(); return count; }
    public int enumerate(Thread[] array) { return enumerate(array,true); }
    public int enumerate(Thread[] array, boolean recurse) {
        Objects.requireNonNull(array); int count = 0;
        for (var thread : threads(recurse)) { if (count == array.length) break; array[count++] = thread; }
        return count;
    }
    public final void interrupt() { for (var thread : threads(true)) thread.interrupt(); }
    public void uncaughtException(Thread thread, Throwable error) {
        if (parent != null) parent.uncaughtException(thread,error);
        else {
            var handler = Thread.getDefaultUncaughtExceptionHandler();
            if (handler != null && handler != this) handler.uncaughtException(thread,error); else error.printStackTrace();
        }
    }
    public String toString() { return "ThreadGroup[name=" + name + ",maxpri=" + maximumPriority + "]"; }
}
