package port.shim;
import org.teavm.jso.JSBody;
/** JavaScript bridges stay in application packages, outside classlib substitution names. */
public final class BrowserPlatform {
    public static String normalize(String text,String form){return normalizeNative(text,form);}
    @JSBody(params={"text","form"},script="return text.normalize(form);")private static native String normalizeNative(String text,String form);
    public static int compare(String a,String b,String locale,int strength){return compareNative(a,b,locale,strength);}
    @JSBody(params={"a","b","locale","strength"},script="const c=new Intl.Collator(locale==='und'?undefined:locale,{sensitivity:['base','accent','variant','variant'][strength]});const n=c.compare(a,b);return n===0&&strength===3?(a<b?-1:a>b?1:0):n;")private static native int compareNative(String a,String b,String locale,int strength);
    public static String agent(){return agentNative();}public static int processors(){return processorsNative();}
    @JSBody(script="return navigator.userAgent;")private static native String agentNative();
    @JSBody(script="return navigator.hardwareConcurrency || 1;")private static native int processorsNative();
}
