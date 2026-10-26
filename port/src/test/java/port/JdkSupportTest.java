package port;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import port.runtime.JdkSupport;
import static org.junit.jupiter.api.Assertions.*;

class JdkSupportTest {
    private static boolean initialized;
    public static final class InitProbe { static { initialized = true; } public InitProbe() { } }
    @Test void classLoadingDoesNotInitializeTheClass() throws Exception {
        Class<?> type = JdkSupport.loadClass(getClass().getClassLoader(), "port.JdkSupportTest$InitProbe");
        assertFalse(initialized); type.getConstructor().newInstance(); assertTrue(initialized);
    }
    @Test void uuidAndLegacyBytesMatchTheJdk() {
        for (long high : new long[]{0,-1,Long.MIN_VALUE,0x123456789abcdef0L}) for (long low : new long[]{0,-1,Long.MIN_VALUE}) {
            var uuid = new java.util.UUID(high,low);
            assertEquals(uuid.toString(),JdkSupport.uuidValue(high,low));
            assertEquals(high,JdkSupport.getMostSignificantBits(uuid)); assertEquals(low,JdkSupport.getLeastSignificantBits(uuid));
        }
        byte[] bytes = {0,1,(byte)128,(byte)255};
        assertEquals(new String(bytes,1,1,3),new String(JdkSupport.highBytes(bytes,1,1,3)));
    }
    @Test void matcherFunctionPreservesReplacementSyntaxAndDetectsMutation() {
        var pattern = java.util.regex.Pattern.compile("([0-9]+)");
        assertEquals("a[12]b[3]",JdkSupport.replaceAll(pattern.matcher("a12b3"), match->"[$1]"));
        var matcher = pattern.matcher("1 2");
        assertThrows(java.util.ConcurrentModificationException.class,()->JdkSupport.replaceAll(matcher, match->{matcher.find();return "x";}));
    }
    @Test void transferPreservesUnicodeAndDoesNotCloseStreams() throws IOException {
        String text = "ñ😀x".repeat(9000);
        var reader = new StringReader(text);
        var writer = new StringWriter();
        assertEquals(text.length(), JdkSupport.transferTo(reader, writer));
        assertEquals(text, writer.toString());
        assertEquals(-1, reader.read());
        writer.write("open");
        assertThrows(NullPointerException.class, () -> JdkSupport.transferTo(reader, null));
    }
    @Test void pathsFollowJdkResolutionIncludingAbsoluteComponents() {
        Path base = Path.of("/Zomboid");
        assertEquals(base.resolve("a", "b", "/media", "c"), JdkSupport.resolve(base, "a", new String[]{"b", "/media", "c"}));
        assertEquals(new File("a/../b").toPath(), JdkSupport.toPath(new File("a/../b")));
        assertThrows(NullPointerException.class, () -> JdkSupport.resolve(base, "a", null));
    }
    @Test void singleByteReadDistinguishesHighBitsAndEof() throws IOException {
        var input = new ByteArrayInputStream(new byte[]{0, (byte)128, (byte)255});
        assertEquals(0, JdkSupport.read(input));
        assertEquals(128, JdkSupport.read(input));
        assertEquals(255, JdkSupport.read(input));
        assertEquals(-1, JdkSupport.read(input));
    }
}
