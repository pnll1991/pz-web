package port.runtime.text;

/**
 * Browser equivalent of the regex helpers in {@code zombie.util.StringUtils}.
 * Verified cause (round 49, diag144/146): {@code containsWhitespace} is
 * {@code String.matches("(.*?)\\s(.*?)")} and TeaVM's {@code ReluctantGroupQuantifierSet::matches}
 * recurses once per character expansion, so a long recipe line (CraftRecipe::LoadIO) exhausts the
 * JavaScript stack ("Maximum call stack size exceeded", 5504 frames) and aborts
 * ScriptManager::Load -> GameWindow::initShared. The loop below keeps the exact desktop semantics:
 * {@code \s} is [ \t\n\x0B\f\r], and a reluctant {@code (.*?)} on both sides simply means
 * "some whitespace exists". No behaviour of the game is changed, only the mechanism.
 */
public final class StringUtilsBridge {
    private StringUtilsBridge() { }

    /**
     * Exact answer of {@code value.matches("(.*?)\\s(.*?)")}. Java's {@code .} does not match a line
     * terminator, so the two reluctant groups succeed only when the whitespace character has no line
     * terminator before or after it: no line terminators -> any {@code \s} char; exactly one -> true
     * (that char is itself {@code \s}); two or more -> false.
     */
    public static boolean containsWhitespace(String value) {
        if (value == null) return false;
        int firstLine = -1;
        int lineCount = 0;
        boolean anyWhitespace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isWhitespace(c)) anyWhitespace = true;
            if (isLineTerminator(c)) {
                if (firstLine < 0) firstLine = i;
                lineCount++;
            }
        }
        if (lineCount == 0) return anyWhitespace;
        if (lineCount == 1) return isWhitespace(value.charAt(firstLine));
        return false;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 11 || c == '\f' || c == '\r';
    }

    private static boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == 11 || c == '\f' || c == 0x2028 || c == 0x2029;
    }

    public static String removeWhitespace(String value) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && (c < 9 || c > 13)) result.append(c);
        }
        return result.toString();
    }
}
