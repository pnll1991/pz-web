package port.compat.java.text;
import java.util.*;
import org.teavm.jso.*;
public class Collator implements Comparator<Object> {
    public static final int PRIMARY=0,SECONDARY=1,TERTIARY=2,IDENTICAL=3;
    private final String locale; private int strength=TERTIARY;
    protected Collator(String locale){this.locale=locale;}
    public static Collator getInstance(){return getInstance(Locale.getDefault());}
    public static Collator getInstance(Locale locale){return new Collator(Objects.requireNonNull(locale).toLanguageTag());}
    public void setStrength(int strength){if(strength<0||strength>3)throw new IllegalArgumentException();this.strength=strength;}
    public int getStrength(){return strength;}
    public int compare(Object a,Object b){return compare((String)a,(String)b);}
    public int compare(String a,String b){Objects.requireNonNull(a);Objects.requireNonNull(b);return port.shim.BrowserPlatform.compare(a,b,locale,strength);}
}
