package port.compat.java.util;
import java.util.Objects;
public final class HexFormat {
    private final String delimiter,prefix,suffix; private final boolean upper;
    private HexFormat(String d,String p,String s,boolean u){delimiter=d;prefix=p;suffix=s;upper=u;}
    public static HexFormat of(){return ofDelimiter("");}
    public static HexFormat ofDelimiter(String d){return new HexFormat(Objects.requireNonNull(d),"","",false);}
    public HexFormat withUpperCase(){return new HexFormat(delimiter,prefix,suffix,true);}
    public HexFormat withLowerCase(){return new HexFormat(delimiter,prefix,suffix,false);}
    public HexFormat withPrefix(String p){return new HexFormat(delimiter,Objects.requireNonNull(p),suffix,upper);}
    public HexFormat withSuffix(String s){return new HexFormat(delimiter,prefix,Objects.requireNonNull(s),upper);}
    public HexFormat withDelimiter(String d){return new HexFormat(Objects.requireNonNull(d),prefix,suffix,upper);}
    public String formatHex(byte[] bytes){return formatHex(bytes,0,bytes.length);}
    public String formatHex(byte[] bytes,int from,int to){Objects.checkFromToIndex(from,to,bytes.length);var out=new StringBuilder();String digits=upper?"0123456789ABCDEF":"0123456789abcdef";for(int i=from;i<to;i++){if(i>from)out.append(delimiter);out.append(prefix).append(digits.charAt((bytes[i]&255)>>>4)).append(digits.charAt(bytes[i]&15)).append(suffix);}return out.toString();}
    public byte[] parseHex(CharSequence input){String text=input.toString();if(text.isEmpty())return new byte[0];int width=prefix.length()+2+suffix.length(),stride=width+delimiter.length();if((text.length()+delimiter.length())%stride!=0)throw new IllegalArgumentException("Malformed hex");int n=(text.length()+delimiter.length())/stride;byte[] result=new byte[n];for(int i=0;i<n;i++){int p=i*stride;if(!text.startsWith(prefix,p)||!text.startsWith(suffix,p+prefix.length()+2)||i>0&&!text.startsWith(delimiter,p-delimiter.length()))throw new IllegalArgumentException("Malformed hex");int hi=Character.digit(text.charAt(p+prefix.length()),16),lo=Character.digit(text.charAt(p+prefix.length()+1),16);if(hi<0||lo<0)throw new IllegalArgumentException("Invalid hex digit");result[i]=(byte)(hi*16+lo);}return result;}
}
