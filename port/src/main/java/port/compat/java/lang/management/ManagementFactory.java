package port.compat.java.lang.management;
import java.util.*;
/** JVM services do not exist in a page. Browser OS data is exposed without inventing JVM counters. */
public final class ManagementFactory {
    private static UnsupportedOperationException unavailable(String name){return new UnsupportedOperationException("ManagementFactory."+name+": JVM management is unavailable in WebAssembly GC");}
    public static CompilationMXBean getCompilationMXBean(){return null; /* JDK specifies null when no compilation system exists. */}
    public static ClassLoadingMXBean getClassLoadingMXBean(){throw unavailable("getClassLoadingMXBean");}
    public static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans(){throw unavailable("getGarbageCollectorMXBeans");}
    public static List<MemoryPoolMXBean> getMemoryPoolMXBeans(){throw unavailable("getMemoryPoolMXBeans");}
    public static MemoryMXBean getMemoryMXBean(){throw unavailable("getMemoryMXBean");}
    public static ThreadMXBean getThreadMXBean(){throw unavailable("getThreadMXBean");}
    public static RuntimeMXBean getRuntimeMXBean(){throw unavailable("getRuntimeMXBean");}
    public static <T> List<T> getPlatformMXBeans(Class<T> type){throw unavailable("getPlatformMXBeans("+type.getName()+")");}
    public static port.compat.javax.management.MBeanServer getPlatformMBeanServer(){throw unavailable("getPlatformMBeanServer");}
    public static OperatingSystemMXBean getOperatingSystemMXBean(){return new BrowserOS();}
    private static final class BrowserOS implements OperatingSystemMXBean {
        public String getName(){return "Browser";}public String getArch(){return "wasm32-gc";}
        public String getVersion(){return port.shim.BrowserPlatform.agent();}public int getAvailableProcessors(){return port.shim.BrowserPlatform.processors();}
        public double getSystemLoadAverage(){return -1; /* Defined by JDK for an unavailable load average. */}
        public javax.management.ObjectName getObjectName(){throw unavailable("OperatingSystemMXBean.getObjectName");}
    }
}
