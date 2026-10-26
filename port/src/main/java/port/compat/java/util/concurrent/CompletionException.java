package port.compat.java.util.concurrent;
public class CompletionException extends RuntimeException {
    public CompletionException(Throwable cause){super(cause);} public CompletionException(String message,Throwable cause){super(message,cause);}
}
