package port.compat.javax.management;
public class ReflectionException extends JMException {
    private final Exception target;
    public ReflectionException(Exception exception){target=exception;}
    public ReflectionException(Exception exception,String message){super(message);target=exception;}
    public Exception getTargetException(){return target;}
    @Override public Throwable getCause(){return target;}
}
