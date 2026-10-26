package port.compiler;

import org.teavm.extension.spi.substitution.SubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/** Exercise the 0.16 substitution SPI without inventing a complete LWJGL class. */
public final class PlatformSubstitutions implements SubstitutionPolicy {
    @Override public void contribute(SubstitutionSink sink) {
        sink.selectClasses(name -> name.equals("port.demo.lwjgl.GL11")).replacePackage("port.demo.lwjgl", "port.shim");
        // A class is eligible only when this project supplies its exact replacement.
        // ConcurrentHashMap itself stays in TeaVM; only its nested KeySetView is ours.
        var cache = new java.util.HashMap<String, Boolean>();
        sink.selectClasses(name -> cache.computeIfAbsent(name, n ->
                !n.equals("java.util.concurrent.ConcurrentHashMap") &&
                PlatformSubstitutions.class.getClassLoader().getResource(
                        "port/compat/" + n.replace('.', '/') + ".class") != null))
                .packagePrefix("port.compat");
    }
}
