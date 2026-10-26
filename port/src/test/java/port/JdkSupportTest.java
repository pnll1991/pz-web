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
