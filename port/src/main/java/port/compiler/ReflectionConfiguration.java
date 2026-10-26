package port.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

public final class ReflectionConfiguration extends SimpleReflectionPolicy {
    @Override protected void setup() {
        try {
            var names = new HashSet<String>();
            for (String raw : Files.readAllLines(Path.of(System.getProperty("pz.reflection")))) {
                String name = raw.strip();
                if (!name.isEmpty() && !name.startsWith("#")) names.add(name);
            }
            selectClasses(cls -> cls != null && names.contains(cls.name())).reflectablePublicMembers().foundByName();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read reflection configuration", error);
        }
    }
}
