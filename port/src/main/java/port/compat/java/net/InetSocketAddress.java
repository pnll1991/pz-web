package port.compat.java.net;
public class InetSocketAddress extends SocketAddress {
    private final String host;private final int port;private final InetAddress address;
    private static int port(int value){if(value<0||value>65535)throw new IllegalArgumentException("Port");return value;}
    public InetSocketAddress(int port){this((InetAddress)null,port);}
    public InetSocketAddress(InetAddress address,int port){this.port=port(port);try{this.address=address==null?InetAddress.getByAddress(new byte[4]):address;}catch(java.net.UnknownHostException e){throw new AssertionError(e);}host=this.address.getHostName();}
    public InetSocketAddress(String host,int port){this.host=java.util.Objects.requireNonNull(host);this.port=port(port);InetAddress found;try{found=InetAddress.getByName(host);}catch(java.net.UnknownHostException error){found=null;}address=found;}
    private InetSocketAddress(String host,int port,boolean unresolved){this.host=java.util.Objects.requireNonNull(host);this.port=port(port);address=null;}
    public static InetSocketAddress createUnresolved(String host,int port){return new InetSocketAddress(host,port,true);}
    public InetAddress getAddress(){return address;}public String getHostName(){return host;}public String getHostString(){return host;}public int getPort(){return port;}public boolean isUnresolved(){return address==null;}
    public boolean equals(Object other){return other instanceof InetSocketAddress a&&port==a.port&&(address==null?a.address==null&&host.equalsIgnoreCase(a.host):address.equals(a.address));}
    public int hashCode(){return port+(address==null?host.toLowerCase(java.util.Locale.ROOT).hashCode():address.hashCode());}
}
