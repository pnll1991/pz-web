package port;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import port.runtime.text.StringUtilsBridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Own synthetic fixtures: the bridge must answer exactly what the desktop regex answers. */
public class StringUtilsBridgeTest {
    private static final Pattern DESKTOP = Pattern.compile("(.*?)\\s(.*?)");

    @Test
    void containsWhitespaceMatchesDesktopRegex() {
        String[] samples = {"", " ", "a b", "\t", "\n", "a\nb", "a\n b", "a\nb\nc", " \n ", "\n\r",
            "CraftRecipe JoinkedSausages items=Can2,Can2 result=Item.JoinkedSausages",
            "long\tline\u000Bwith\u000Cvertical\u2028tabs\r", "\u2028", "a\u2028b"};
        for (String sample : samples) {
            assertEquals(DESKTOP.matcher(sample).matches(), StringUtilsBridge.containsWhitespace(sample),
                "sample=" + sample.replace('\n', '~').replace('\r', '%'));
        }
    }

    @Test
    void removeWhitespaceMatchesDesktopReplaceAll() {
        String[] samples = {"", " a b\t", "\n\r\u000B\u000C", "C  a  t", "x\u2028y"};
        for (String sample : samples) {
            assertEquals(sample.replaceAll("\\s", ""), StringUtilsBridge.removeWhitespace(sample));
        }
    }
}
