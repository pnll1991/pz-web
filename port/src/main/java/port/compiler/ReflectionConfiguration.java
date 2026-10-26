package port.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

public final class ReflectionConfiguration extends SimpleReflectionPolicy {
    @Override protected void setup() {
        try {
            for (String raw : Files.readAllLines(Path.of(System.getProperty("pz.reflection")))) {
                String name = raw.strip();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    selectClasses(named(name)).reflectablePublicMembers().foundByName();
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read reflection configuration", error);
        }
    }
}
