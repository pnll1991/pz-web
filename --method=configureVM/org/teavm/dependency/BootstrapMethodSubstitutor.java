package org.teavm.dependency;

import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

@FunctionalInterface
public interface BootstrapMethodSubstitutor {
   ValueEmitter substitute(DynamicCallSite var1, ProgramEmitter var2);
}
