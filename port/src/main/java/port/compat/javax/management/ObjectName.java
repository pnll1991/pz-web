package port.compat.javax.management;
import java.util.*;
import javax.management.MalformedObjectNameException;
/** JMX names are data; this does not supply a JVM management server. */
public final class ObjectName implements Comparable<ObjectName>,java.io.Serializable {
    private final String name,domain,canonical;
    private final Map<String,String> properties;
    private final boolean pattern;
    public ObjectName(String text)throws MalformedObjectNameException{
        Objects.requireNonNull(text);if(text.isEmpty())text="*:*";name=text;int split=text.indexOf(':');if(split<0)throw invalid(text);domain=text.substring(0,split);if(domain.indexOf('\n')>=0)throw invalid(text);
        var values=new TreeMap<String,String>();boolean wildcard=false,pattern=domain.contains("*")||domain.contains("?");int p=split+1;
        if(p==text.length())throw invalid(text);
        while(p<text.length()){
            if(text.charAt(p)=='*'&&(p+1==text.length()||text.charAt(p+1)==',')){if(wildcard)throw invalid(text);wildcard=true;p++;}
            else{int equal=text.indexOf('=',p);if(equal<0)throw invalid(text);String key=text.substring(p,equal);if(key.isEmpty()||has(key,",:=*?\n"))throw invalid(text);p=equal+1;int start=p;
                if(p<text.length()&&text.charAt(p)=='"'){p++;boolean closed=false;while(p<text.length()){char c=text.charAt(p++);if(c=='"'){closed=true;break;}if(c=='\n')throw invalid(text);if(c=='\\'){if(p==text.length()||"\\\"n*?".indexOf(text.charAt(p++))<0)throw invalid(text);}else if(c=='*'||c=='?')pattern=true;}if(!closed)throw invalid(text);}
                else{while(p<text.length()&&text.charAt(p)!=','){char c=text.charAt(p++);if(":=\n\"".indexOf(c)>=0)throw invalid(text);if(c=='*'||c=='?')pattern=true;}}
                if(values.put(key,text.substring(start,p))!=null)throw invalid(text);
            }
            if(p<text.length()){if(text.charAt(p++)!=','||p==text.length())throw invalid(text);}
        }
        properties=Collections.unmodifiableMap(values);this.pattern=pattern||wildcard;var parts=new ArrayList<String>();values.forEach((key,value)->parts.add(key+"="+value));if(wildcard)parts.add("*");canonical=domain+":"+String.join(",",parts);
    }
    private static boolean has(String value,String forbidden){for(int i=0;i<value.length();i++)if(forbidden.indexOf(value.charAt(i))>=0)return true;return false;}
    private static MalformedObjectNameException invalid(String name){return new MalformedObjectNameException("Invalid ObjectName: "+name);}
    public String getDomain(){return domain;}public String getCanonicalName(){return canonical;}public String getKeyProperty(String key){return properties.get(key);}public Hashtable<String,String> getKeyPropertyList(){return new Hashtable<>(properties);}public boolean isPattern(){return pattern;}
    public static ObjectName getInstance(String name)throws MalformedObjectNameException{return new ObjectName(name);}
    public int compareTo(ObjectName other){int order=domain.compareTo(other.domain);if(order==0)order=properties.getOrDefault("type","").compareTo(other.properties.getOrDefault("type",""));return order==0?canonical.compareTo(other.canonical):order;}
    @Override public String toString(){return name;}@Override public int hashCode(){return canonical.hashCode();}@Override public boolean equals(Object other){return other instanceof ObjectName n&&canonical.equals(n.canonical);}
}
