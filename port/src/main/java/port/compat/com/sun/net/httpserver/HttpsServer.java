package port.compat.com.sun.net.httpserver;
public abstract class HttpsServer extends HttpServer {
    public static HttpsServer create(java.net.InetSocketAddress address,int backlog)throws java.io.IOException{throw unavailable();}
    public void setHttpsConfigurator(HttpsConfigurator value){throw unavailable();}
}
