package port.compat.java.lang;

public class ExceptionInInitializerError extends LinkageError {
    public ExceptionInInitializerError() { }
    public ExceptionInInitializerError(String message) { super(message); }
    public ExceptionInInitializerError(Throwable cause) { super(null, cause); }
    public Throwable getException() { return getCause(); }
}
