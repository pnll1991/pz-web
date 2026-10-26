package port.compat.java.lang;
public class SecurityManager {
    public void checkPackageAccess(String pkg){throw new UnsupportedOperationException("SecurityManager.checkPackageAccess: JVM permission policy is not supported in the browser: "+pkg);}
    protected Class<?>[] getClassContext(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->stream.skip(1).map(StackWalker.StackFrame::getDeclaringClass).toArray(Class<?>[]::new));}
}
