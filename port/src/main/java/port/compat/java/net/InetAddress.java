package port.compat.java.net;
import java.net.UnknownHostException;
import java.util.*;
public class InetAddress implements java.io.Serializable {
    private final byte[] bytes;private final String name;
    private InetAddress(byte[] bytes,String name){this.bytes=bytes.clone();this.name=name;}
    public static InetAddress getByAddress(byte[] bytes)throws UnknownHostException{if(bytes.length!=4&&bytes.length!=16)throw new UnknownHostException("Invalid address length");return new InetAddress(bytes,null);}
    public static InetAddress getByName(String host)throws UnknownHostException{
        if(host==null||host.isEmpty()||host.equalsIgnoreCase("localhost"))return getLoopbackAddress();
        String[] parts=host.split("\\.",-1);if(parts.length!=4)throw new UnknownHostException("Browser DNS/raw socket resolution unavailable: "+host);
        byte[] bytes=new byte[4];try{for(int i=0;i<4;i++){if(parts[i].isEmpty()||!parts[i].chars().allMatch(Character::isDigit))throw new NumberFormatException();int n=Integer.parseInt(parts[i]);if(n<0||n>255)throw new NumberFormatException();bytes[i]=(byte)n;}}catch(NumberFormatException error){throw new UnknownHostException(host);}return new InetAddress(bytes,host);
    }
    public static InetAddress[] getAllByName(String host)throws UnknownHostException{return new InetAddress[]{getByName(host)};}
    public static InetAddress getLoopbackAddress(){return new InetAddress(new byte[]{127,0,0,1},"localhost");}
    public static InetAddress getLocalHost()throws UnknownHostException{throw new UnknownHostException("The browser does not expose the host's local network identity");}
    public byte[] getAddress(){return bytes.clone();}
    public String getHostAddress(){if(bytes.length==4)return (bytes[0]&255)+"."+(bytes[1]&255)+"."+(bytes[2]&255)+"."+(bytes[3]&255);var text=new StringBuilder();for(int i=0;i<16;i+=2){if(i>0)text.append(':');text.append(Integer.toHexString((bytes[i]&255)*256+(bytes[i+1]&255)));}return text.toString();}
    public String getHostName(){return name==null?getHostAddress():name;}public String getCanonicalHostName(){return getHostAddress();}
    public boolean isLoopbackAddress(){return bytes.length==4?(bytes[0]&255)==127:java.util.Arrays.equals(bytes,new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1});}
    public boolean equals(Object other){return other instanceof InetAddress a&&Arrays.equals(bytes,a.bytes);}public int hashCode(){return Arrays.hashCode(bytes);}public String toString(){return (name==null?"":name)+"/"+getHostAddress();}
}
