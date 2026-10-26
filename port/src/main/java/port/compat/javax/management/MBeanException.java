package port.compat.javax.management;
public class MBeanException extends JMException {
    private final Exception target;
    public MBeanException(Exception exception){target=exception;}
    public MBeanException(Exception exception,String message){super(message);target=exception;}
    public Exception getTargetException(){return target;}
    @Override public Throwable getCause(){return target;}
}
