package port.compat.java.text;
import org.teavm.jso.JSBody;
public final class Normalizer {
    public enum Form { NFD,NFC,NFKD,NFKC }
    public static String normalize(CharSequence text,Form form){return port.shim.BrowserPlatform.normalize(java.util.Objects.requireNonNull(text).toString(),java.util.Objects.requireNonNull(form).name());}
    public static boolean isNormalized(CharSequence text,Form form){return text.toString().equals(normalize(text,form));}
}
