package port.compat.com.sun.net.httpserver;
public abstract class Authenticator {
    public abstract Result authenticate(HttpExchange exchange);
    public abstract static class Result {}
}
