package port.compat.com.sun.net.httpserver;
public abstract class HttpServer {
    static UnsupportedOperationException unavailable(){return new UnsupportedOperationException("HttpServer: a browser page cannot listen on a TCP port");}
    public static HttpServer create(java.net.InetSocketAddress address,int backlog)throws java.io.IOException{throw unavailable();}
    public HttpContext createContext(String path,HttpHandler handler){throw unavailable();}
    public java.net.InetSocketAddress getAddress(){throw unavailable();}public void setExecutor(java.util.concurrent.Executor executor){throw unavailable();}public void start(){throw unavailable();}public void stop(int delay){throw unavailable();}
}
