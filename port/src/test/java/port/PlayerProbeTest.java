package port;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import port.compiler.PlayerProbeCompatibility;
import port.runtime.PlayerProbe;

/** Ronda 80 (frente M): cada stub g* de PlayerProbe tiene exactamente una reescritura TeaVM. */
class PlayerProbeTest {
    @Test void everyStubHasARewrite() {
        Set<String> stubs = Arrays.stream(PlayerProbe.class.getDeclaredMethods())
            .filter(m -> Modifier.isStatic(m.getModifiers()))
            .map(java.lang.reflect.Method::getName)
            .filter(n -> n.length() > 1 && n.charAt(0) == 'g' && Character.isUpperCase(n.charAt(1)))
            .collect(Collectors.toSet());
        assertEquals(PlayerProbeCompatibility.rewrittenStubs(), stubs);
    }
}
