package org.teavm.model.optimization;

import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;

public interface InliningStep {
   InliningStep tryInline(MethodReference var1, ProgramReader var2, InliningContext var3);
}
