package port.shim;
import java.util.Locale;
import java.util.Objects;
import org.teavm.jso.JSBody;
/** JavaScript bridges stay in application packages, outside classlib substitution names. */
public final class BrowserPlatform {
    public static String normalize(String text,String form){return normalizeNative(text,form);}
    @JSBody(params={"text","form"},script="return text.normalize(form);")private static native String normalizeNative(String text,String form);
    public static int compare(String a,String b,String locale,int strength){return compareNative(a,b,locale,strength);}
    @JSBody(params={"a","b","locale","strength"},script="const c=new Intl.Collator(locale==='und'?undefined:locale,{sensitivity:['base','accent','variant','variant'][strength]});const n=c.compare(a,b);return n===0&&strength===3?(a<b?-1:a>b?1:0):n;")private static native int compareNative(String a,String b,String locale,int strength);
    // Locale facts come from the browser itself; nothing is invented and no OS is faked.
    public static String localeTag(){return localeTagNative();}
    @JSBody(script="return navigator.language || '';")private static native String localeTagNative();
    /** Primary subtag of a BCP-47 tag, lowercased, e.g. "es-AR" -> "es". */
    public static String localeLanguage(String tag){
        String value = Objects.requireNonNull(tag);
        int dash = value.indexOf('-');
        return (dash < 0 ? value : value.substring(0, dash)).toLowerCase(java.util.Locale.ROOT);
    }
    /** Region subtag when the tag has one, uppercased, e.g. "es-AR" -> "AR"; otherwise empty. */
    public static String localeRegion(String tag){
        String value = Objects.requireNonNull(tag);
        int dash = value.indexOf('-');
        return dash < 0 ? "" : value.substring(dash + 1).toUpperCase(java.util.Locale.ROOT);
    }
    public static String agent(){return agentNative();}public static int processors(){return processorsNative();}
    @JSBody(script="return navigator.userAgent;")private static native String agentNative();
    @JSBody(script="return navigator.hardwareConcurrency || 1;")private static native int processorsNative();
}
