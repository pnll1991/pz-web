package port.compiler;

import org.teavm.extension.spi.substitution.SubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/** Exercise the 0.16 substitution SPI without inventing a complete LWJGL class. */
public final class PlatformSubstitutions implements SubstitutionPolicy {
    @Override public void contribute(SubstitutionSink sink) {
        sink.substitutePackage("port.demo.lwjgl", "port.shim");
    }
}
