package port.runtime;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import port.shim.BrowserResources;
public final class JdkTask4Support {
    private static int extraNanos(java.util.concurrent.TimeUnit unit,long timeout){return switch(unit){case NANOSECONDS->(int)(timeout%1_000_000);case MICROSECONDS->(int)(timeout%1000)*1000;default->0;};}
    public static void timedWait(java.util.concurrent.TimeUnit unit,Object monitor,long timeout)throws InterruptedException{if(timeout>0)monitor.wait(unit.toMillis(timeout),extraNanos(unit,timeout));}
    public static void timedJoin(java.util.concurrent.TimeUnit unit,Thread thread,long timeout)throws InterruptedException{if(timeout>0)thread.join(unit.toMillis(timeout),extraNanos(unit,timeout));}
    public static void sleep(java.util.concurrent.TimeUnit unit,long timeout)throws InterruptedException{if(timeout>0){long millis=unit.toMillis(timeout);if(extraNanos(unit,timeout)>0&&millis<Long.MAX_VALUE)millis++;Thread.sleep(millis);}}
    public static boolean setExecutable(File file,boolean executable,boolean ownerOnly){return !executable&&file.exists();}
    public static String getPackageName(Class<?> cls){while(cls.isArray())cls=cls.getComponentType();if(cls.isPrimitive())return "java.lang";String name=cls.getName();int dot=name.lastIndexOf('.');return dot<0?"":name.substring(0,dot);}
    public static URL getResource(Class<?> cls,String name){Objects.requireNonNull(name);return BrowserResources.resource(name.startsWith("/")?name.substring(1):getPackageName(cls).replace('.','/')+(getPackageName(cls).isEmpty()?"":"/")+name);}
    public static URL getResource(ClassLoader loader,String name){return BrowserResources.resource(name);}
    public static URL getSystemResource(String name){return BrowserResources.resource(name);}
    public static Enumeration<URL> getResources(ClassLoader loader,String name)throws IOException{return BrowserResources.resources(name);}
    public static Enumeration<URL> getSystemResources(String name)throws IOException{return BrowserResources.resources(name);}
    public static InputStream getResourceAsStream(ClassLoader loader,String name){return BrowserResources.stream(name);}
    public static InputStream getSystemResourceAsStream(String name){return BrowserResources.stream(name);}
    public static InputStream getResourceAsStream(Class<?> cls,String name){URL url=getResource(cls,name);try{return url==null?null:url.openStream();}catch(IOException e){return null;}}
    public static String getSpecificationVersion(Package pkg){return null; /* No package manifests are attached to AOT Class metadata. JDK permits null. */}
    public static Type getGenericSuperclass(Class<?> cls){
        // TeaVM 0.16.0 retains no generic superclass signature: teavm-classlib 0.16.0 implements
        // getGenericType/getGenericReturnType/getGenericParameterTypes only (verified by scanning every
        // .class in teavm-classlib-0.16.0.jar: no "genericSuperclass" string anywhere), so the declared
        // type arguments of the superclass simply are not in the AOT metadata. The JDK returns the raw
        // Class itself whenever the superclass is not generic, which is the case for every class the
        // boot exposes to Kahlua (LuaJavaClassExposer.exposeLikeJavaByClass walks getInterfaces() and
        // getGenericSuperclass() purely to recurse), so the raw superclass is the faithful answer here
        // and null for java.lang.Object, interfaces and primitives, as the JDK also returns.
        return cls.getSuperclass();
    }
    public static Type[] getGenericExceptionTypes(Method method){
        // Checked exception types never carry type arguments, so the JDK's getGenericExceptionTypes()
        // returns the same classes as getExceptionTypes(); TeaVM only retains the latter.
        return method.getExceptionTypes();
    }
    public static boolean canAccess(Field field,Object target){if(Modifier.isStatic(field.getModifiers())?target!=null:target==null||!field.getDeclaringClass().isInstance(target))throw new IllegalArgumentException();return field.isAccessible()||Modifier.isPublic(field.getModifiers())&&Modifier.isPublic(field.getDeclaringClass().getModifiers());}
    public static boolean trySetAccessible(Field field){field.setAccessible(true);return field.isAccessible();}
    // Round 79: zip archives get our read-only ZipFileSystem (ImagePyramid spawn/world map pyramids).
    public static java.nio.file.FileSystem newFileSystem(java.nio.file.Path path)throws IOException{String name=String.valueOf(path.getFileName()).toLowerCase(java.util.Locale.ROOT);if(name.endsWith(".zip")||name.endsWith(".jar"))return ZipFileSystem.open(path);throw new java.nio.file.ProviderNotFoundException("No browser filesystem provider for "+path);}
    public static java.nio.file.FileSystem newFileSystem(java.nio.file.Path path,Map<String,?> env)throws IOException{Objects.requireNonNull(env);return newFileSystem(path);}
    public static Map<Thread,StackTraceElement[]> getAllStackTraces(){return port.compat.java.lang.ThreadGroup.stackTraces();}
    public static float[] RGBtoHSB(int r,int g,int b,float[] values){if(values==null)values=new float[3];int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));float saturation=max==0?0:(float)(max-min)/max;float hue=0;if(saturation!=0){float red=(float)(max-r)/(max-min),green=(float)(max-g)/(max-min),blue=(float)(max-b)/(max-min);hue=r==max?blue-green:g==max?2+red-blue:4+green-red;hue/=6;if(hue<0)hue++;}values[0]=hue;values[1]=saturation;values[2]=max/255f;return values;}
    public static String formatMessage(java.util.logging.Formatter formatter,java.util.logging.LogRecord record){String message=record.getMessage();Object[] args=record.getParameters();if(args==null||args.length==0||message==null)return message;boolean pattern=false;for(int i=0;i+1<message.length();i++)if(message.charAt(i)=='{'&&message.charAt(i+1)>='0'&&message.charAt(i+1)<='9'){pattern=true;break;}if(!pattern)return message;try{return java.text.MessageFormat.format(message,args);}catch(IllegalArgumentException error){return message;}}
    private static final Map<java.util.logging.Logger,java.util.logging.Level> levels=new WeakHashMap<>();
    private static final Map<java.util.logging.Logger,Boolean> parents=new WeakHashMap<>();
    public static void setUseParentHandlers(java.util.logging.Logger logger,boolean enabled){parents.put(logger,enabled);}
    public static boolean getUseParentHandlers(java.util.logging.Logger logger){return parents.getOrDefault(logger,true);}
    public static void setLevel(java.util.logging.Logger logger,java.util.logging.Level level){if(level==null)levels.remove(logger);else levels.put(logger,level);}
    public static java.util.logging.Level getLevel(java.util.logging.Logger logger){return levels.get(logger);}
    public static boolean isLoggable(java.util.logging.Logger logger,java.util.logging.Level level){Objects.requireNonNull(level);for(var current=logger;current!=null;current=current.getParent()){var threshold=levels.get(current);if(threshold!=null)return threshold!=java.util.logging.Level.OFF&&level.intValue()>=threshold.intValue();}return level.intValue()>=java.util.logging.Level.INFO.intValue();}
    private static final Map<java.util.logging.Logger,List<port.compat.java.util.logging.Handler>> handlers=new WeakHashMap<>();
    private static List<port.compat.java.util.logging.Handler> handlers(java.util.logging.Logger logger){return handlers.computeIfAbsent(logger,key->{var list=new ArrayList<port.compat.java.util.logging.Handler>();if("".equals(key.getName()))list.add(new port.compat.java.util.logging.ConsoleHandler());return list;});}
    public static void addHandler(java.util.logging.Logger logger,port.compat.java.util.logging.Handler handler){handlers(logger).add(Objects.requireNonNull(handler));}
    public static void removeHandler(java.util.logging.Logger logger,port.compat.java.util.logging.Handler handler){handlers(logger).remove(handler);}
    public static port.compat.java.util.logging.Handler[] getHandlers(java.util.logging.Logger logger){return handlers(logger).toArray(port.compat.java.util.logging.Handler[]::new);}
    public static void log(java.util.logging.Logger logger,java.util.logging.LogRecord record){if(!isLoggable(logger,record.getLevel()))return;if(record.getLoggerName()==null)record.setLoggerName(logger.getName());for(var current=logger;current!=null;current=current.getParent()){for(var handler:getHandlers(current))handler.publish(record);if(!getUseParentHandlers(current))break;}}
    @org.teavm.jso.JSBody(params={"level","message"},script="(level>=1000?console.error:level>=900?console.warn:console.log)(message);")public static native void console(int level,String message);
    public static String getImplementationVersion(Package pkg){return null;}
    public static boolean isAnonymousClass(Class<?> cls){
        // TeaVM 0.16.0 retains no InnerClasses attribute (verified: teavm-classlib-0.16.0.jar has no
        // org/teavm/java/java/lang/Class*.class entries at all, and Class.isAnonymousClass is absent),
        // so the JDK's attribute lookup cannot be reproduced. javac names an anonymous class
        // "<enclosing>$<n>", i.e. the segment after the last '$' is purely numeric and the simple name
        // is empty, which is exactly the condition the JDK reports from that attribute. Local and
        // member classes ("<enclosing>$<n><Name>") keep a simple name and stay false, as in the JDK.
        // The only consumer found in the pinned jar is LuaJavaClassExposer.exposeLikeJava (bytecode
        // offset 48), which uses it solely to skip exposeStatics for anonymous classes.
        String name = cls.getName();
        int dollar = name.lastIndexOf('$');
        if (dollar < 0 || dollar == name.length() - 1) return false;
        for (int i = dollar + 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }
    public static boolean setWritable(File file,boolean writable,boolean ownerOnly){return setWritableNative(file.getAbsolutePath(),writable);}
    @org.teavm.jso.JSBody(params={"path","writable"},script="return pzRuntime.vfs.setAttribute(pzRuntime.normalizePath(path),'readOnly',!writable);")private static native boolean setWritableNative(String path,boolean writable);
    public static java.nio.ByteBuffer slice(java.nio.ByteBuffer buffer,int index,int length){Objects.checkFromIndexSize(index,length,buffer.limit());return buffer.duplicate().position(index).limit(index+length).slice();}
    public static <K,V> port.compat.java.util.concurrent.ConcurrentHashMap.KeySetView<K,V> keySet(java.util.concurrent.ConcurrentHashMap<K,V> map){return new port.compat.java.util.concurrent.ConcurrentHashMap.KeySetView<>(map,null);}
    public static int HSBtoRGB(float hue,float saturation,float brightness){int r,g,b;if(saturation==0){r=g=b=(int)(brightness*255+0.5f);}else{float h=(hue-(float)Math.floor(hue))*6,f=h-(float)Math.floor(h),p=brightness*(1-saturation),q=brightness*(1-saturation*f),t=brightness*(1-saturation*(1-f));float rr,gg,bb;switch((int)h){case 0:rr=brightness;gg=t;bb=p;break;case 1:rr=q;gg=brightness;bb=p;break;case 2:rr=p;gg=brightness;bb=t;break;case 3:rr=p;gg=q;bb=brightness;break;case 4:rr=t;gg=p;bb=brightness;break;default:rr=brightness;gg=p;bb=q;}r=(int)(rr*255+0.5f);g=(int)(gg*255+0.5f);b=(int)(bb*255+0.5f);}return 0xff000000|r<<16|g<<8|b;}

}
